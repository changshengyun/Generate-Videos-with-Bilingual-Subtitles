#!/usr/bin/env python3
"""Validate LyricCaptioner's repository-level Brain–Limbs contract.

This module intentionally uses only the Python 3.9 standard library.  Its TOML
reader supports the small, string-oriented subset used by this repository; it
is not a general TOML parser.
"""

from __future__ import annotations

import argparse
import ast
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Mapping, Optional, Sequence, Tuple


REPO_CONTRACT_ENFORCED = "REPO_CONTRACT_ENFORCED"
PLATFORM_BOUNDARY = (
    "Repository checks cannot hard-block Codex platform tool calls; "
    "runtime topology still requires forward verification."
)

SPAWN_INTERNAL_BRAIN = "SPAWN_INTERNAL_BRAIN"
REUSE_INTERNAL_BRAIN = "REUSE_INTERNAL_BRAIN"
CREATE_EXTERNAL_THREAD = "CREATE_EXTERNAL_THREAD"

CANONICAL_BRAIN_RE = re.compile(r"^/root/[a-z0-9_]+(?:/[a-z0-9_]+)*$")
ACTIVE_BRAIN_RE = re.compile(
    r"Active Brain canonical path:\s*`?(?P<path>/root(?:/[a-z0-9_]+)+)`?"
)

MANAGED_TEXT_FILES = (
    "AGENTS.md",
    ".agents/multi-agent-development.md",
    ".codex/config.toml",
    ".codex/agents/brain.toml",
    ".codex/agents/limbs.toml",
    ".codex/skills/begin/SKILL.md",
    ".codex/skills/begin/agents/openai.yaml",
    ".codex/skills/lyriccaptioner-brain/SKILL.md",
    ".codex/skills/lyriccaptioner-brain/agents/openai.yaml",
)


class ContractViolation(ValueError):
    """Raised when a simulated runtime action violates the frozen topology."""


class ContractTomlError(ValueError):
    """Raised for unsupported or malformed project contract TOML."""


@dataclass(frozen=True)
class RouteDecision:
    action: str
    task_name: Optional[str] = None
    reconciliation_required: bool = False


def _read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def _parse_scalar(raw: str, source: Path, line_number: int):
    value = raw.strip()
    if not value:
        raise ContractTomlError(f"{source}:{line_number}: empty value")
    if value in ("true", "false"):
        return value == "true"
    if re.fullmatch(r"-?[0-9]+", value):
        return int(value)
    if value.startswith(('"', "'")):
        try:
            parsed = ast.literal_eval(value)
        except (SyntaxError, ValueError) as exc:
            raise ContractTomlError(
                f"{source}:{line_number}: invalid quoted value"
            ) from exc
        if not isinstance(parsed, str):
            raise ContractTomlError(f"{source}:{line_number}: expected string")
        return parsed
    raise ContractTomlError(
        f"{source}:{line_number}: unsupported TOML subset value {value!r}"
    )


def parse_contract_toml(path: Path) -> Dict[str, Dict[str, object]]:
    """Parse only the simple TOML subset used by project Agent contracts."""

    lines = _read(path).splitlines()
    tables: Dict[str, Dict[str, object]] = {"": {}}
    current = ""
    index = 0
    while index < len(lines):
        raw_line = lines[index]
        stripped = raw_line.strip()
        line_number = index + 1
        index += 1
        if not stripped or stripped.startswith("#"):
            continue
        if stripped.startswith("["):
            match = re.fullmatch(r"\[([A-Za-z0-9_.-]+)\]", stripped)
            if not match:
                raise ContractTomlError(
                    f"{path}:{line_number}: unsupported or malformed table"
                )
            current = match.group(1)
            if current in tables:
                raise ContractTomlError(f"{path}:{line_number}: duplicate table {current}")
            tables[current] = {}
            continue

        assignment = re.match(r"([A-Za-z0-9_-]+)\s*=\s*(.*)$", stripped)
        if not assignment:
            raise ContractTomlError(f"{path}:{line_number}: malformed assignment")
        key, value = assignment.groups()
        if key in tables[current]:
            raise ContractTomlError(f"{path}:{line_number}: duplicate key {key}")

        if value.startswith('"""'):
            remainder = value[3:]
            parts: List[str] = []
            if '"""' in remainder:
                content, trailing = remainder.split('"""', 1)
                if trailing.strip() and not trailing.lstrip().startswith("#"):
                    raise ContractTomlError(
                        f"{path}:{line_number}: trailing multiline content"
                    )
                parts.append(content)
            else:
                parts.append(remainder)
                while index < len(lines):
                    candidate = lines[index]
                    index += 1
                    if '"""' in candidate:
                        content, trailing = candidate.split('"""', 1)
                        if trailing.strip() and not trailing.lstrip().startswith("#"):
                            raise ContractTomlError(
                                f"{path}:{index}: trailing multiline content"
                            )
                        parts.append(content)
                        break
                    parts.append(candidate)
                else:
                    raise ContractTomlError(
                        f"{path}:{line_number}: unterminated multiline string"
                    )
            tables[current][key] = "\n".join(parts)
        else:
            tables[current][key] = _parse_scalar(value, path, line_number)
    return tables


def is_canonical_brain_path(value: Optional[str]) -> bool:
    return bool(value and CANONICAL_BRAIN_RE.fullmatch(value))


def cold_start_route(
    persisted_brain_path: Optional[str], runtime_live: bool = False
) -> RouteDecision:
    persisted = persisted_brain_path.strip() if persisted_brain_path else None
    if persisted and not is_canonical_brain_path(persisted):
        raise ContractViolation(f"invalid active Brain canonical path: {persisted!r}")
    if runtime_live and not persisted:
        raise ContractViolation(
            "runtime reports a live Brain but no persisted canonical path exists"
        )
    if persisted and runtime_live:
        return RouteDecision(REUSE_INTERNAL_BRAIN)
    if persisted:
        return RouteDecision(
            SPAWN_INTERNAL_BRAIN,
            task_name=persisted.rsplit("/", 1)[-1],
            reconciliation_required=True,
        )
    return RouteDecision(
        SPAWN_INTERNAL_BRAIN,
        task_name="lyriccaptioner_brain",
        reconciliation_required=False,
    )


def route_natural_language(
    prompt: str,
    persisted_brain_path: Optional[str] = None,
    runtime_live: bool = False,
) -> RouteDecision:
    normalized = re.sub(r"\s+", " ", prompt.strip())
    explicit_external = (
        re.search(r"独立\s*Codex\s*窗口", normalized, re.IGNORECASE)
        or re.search(r"独立\s*Codex\s*侧边栏任务", normalized, re.IGNORECASE)
        or re.search(r"独立\s*Codex\s*任务", normalized, re.IGNORECASE)
    )
    if explicit_external:
        return RouteDecision(CREATE_EXTERNAL_THREAD)
    return cold_start_route(persisted_brain_path, runtime_live)


def brain_delivery_tool(runtime_live: bool, runtime_running: bool) -> str:
    if not runtime_live:
        raise ContractViolation("cannot deliver to a Brain that is not live")
    return "send_message" if runtime_running else "followup_task"


def validate_runtime_topology(
    actor: str,
    action: str,
    active_brain: Optional[str],
    parent: Optional[str] = None,
    explicit_external_request: bool = False,
) -> bool:
    actor_normalized = actor.strip()
    action_normalized = action.strip().upper()
    if action_normalized == "SPAWN_LIMBS":
        if actor_normalized == "/root" or actor_normalized.lower() == "root":
            raise ContractViolation("root must never spawn Limbs directly")
        if not is_canonical_brain_path(active_brain):
            raise ContractViolation("SPAWN_LIMBS requires a canonical active Brain")
        if actor_normalized != active_brain:
            raise ContractViolation("only the active Brain may spawn Limbs")
        if parent != active_brain:
            raise ContractViolation("Limbs parent must equal the active Brain")
        return True
    if action_normalized in (SPAWN_INTERNAL_BRAIN, REUSE_INTERNAL_BRAIN):
        if actor_normalized not in ("root", "/root"):
            raise ContractViolation("only root coordinates the active Brain lifecycle")
        return True
    if action_normalized == CREATE_EXTERNAL_THREAD:
        if actor_normalized not in ("root", "/root"):
            raise ContractViolation("Brain must never call create_thread")
        if not explicit_external_request:
            raise ContractViolation("external thread creation requires explicit user language")
        return True
    raise ContractViolation(f"unsupported topology action: {action}")


def _extract_frontmatter(path: Path) -> Tuple[Mapping[str, str], str]:
    text = _read(path)
    lines = text.splitlines()
    if not lines or lines[0].strip() != "---":
        raise ValueError(f"{path}: missing YAML frontmatter")
    try:
        end = next(i for i in range(1, len(lines)) if lines[i].strip() == "---")
    except StopIteration as exc:
        raise ValueError(f"{path}: unterminated YAML frontmatter") from exc
    fields: Dict[str, str] = {}
    for offset, line in enumerate(lines[1:end], start=2):
        match = re.fullmatch(r"([A-Za-z0-9_-]+):\s*(.+)", line)
        if not match:
            raise ValueError(f"{path}:{offset}: malformed frontmatter")
        key, value = match.groups()
        fields[key] = value.strip()
    return fields, "\n".join(lines[end + 1 :])


def _check_skill(skill_dir: Path, expected_name: str) -> List[str]:
    errors: List[str] = []
    skill_path = skill_dir / "SKILL.md"
    yaml_path = skill_dir / "agents" / "openai.yaml"
    try:
        fields, body = _extract_frontmatter(skill_path)
        if set(fields) != {"name", "description"}:
            errors.append(f"{skill_path}: frontmatter must contain only name/description")
        if fields.get("name") != expected_name:
            errors.append(f"{skill_path}: expected name {expected_name}")
        if not fields.get("description"):
            errors.append(f"{skill_path}: description is required")
        if not body.strip():
            errors.append(f"{skill_path}: body is empty")
    except (OSError, ValueError) as exc:
        errors.append(str(exc))

    try:
        yaml_text = _read(yaml_path)
        for key in ("display_name", "short_description", "default_prompt"):
            match = re.search(rf"^\s*{key}:\s*(.+)$", yaml_text, re.MULTILINE)
            if not match:
                errors.append(f"{yaml_path}: missing {key}")
                continue
            raw_value = match.group(1).strip()
            if not re.fullmatch(r'"(?:[^"\\]|\\.)*"', raw_value):
                errors.append(f"{yaml_path}: {key} must be a quoted string")
        default_match = re.search(
            r'^\s*default_prompt:\s*"([^"]*)"', yaml_text, re.MULTILINE
        )
        if default_match and f"${expected_name}" not in default_match.group(1):
            errors.append(f"{yaml_path}: default_prompt must mention ${expected_name}")
        short_match = re.search(
            r'^\s*short_description:\s*"([^"]*)"', yaml_text, re.MULTILINE
        )
        if short_match and not 25 <= len(short_match.group(1)) <= 64:
            errors.append(f"{yaml_path}: short_description must be 25-64 characters")
    except OSError as exc:
        errors.append(str(exc))
    return errors


def _active_brain_from(text: str, source: Path) -> Tuple[Optional[str], List[str]]:
    matches = [match.group("path") for match in ACTIVE_BRAIN_RE.finditer(text)]
    if len(matches) != 1:
        return None, [f"{source}: expected exactly one Active Brain canonical path"]
    if not is_canonical_brain_path(matches[0]):
        return None, [f"{source}: invalid Active Brain canonical path"]
    return matches[0], []


def _governance_ledger_parents(current_task: str) -> Tuple[List[str], List[str]]:
    errors: List[str] = []
    section_match = re.search(
        r"^## GOV-MULTIAGENT-001 Limbs 账本\s*$([\s\S]*?)(?=^## |\Z)",
        current_task,
        re.MULTILINE,
    )
    if not section_match:
        return [], ["docs/CURRENT_TASK.md: missing active governance Limbs ledger"]
    table_lines = [
        line.strip()
        for line in section_match.group(1).splitlines()
        if line.strip().startswith("|")
    ]
    if len(table_lines) < 3:
        return [], ["docs/CURRENT_TASK.md: governance Limbs ledger has no rows"]
    headers = [cell.strip() for cell in table_lines[0].strip("|").split("|")]
    if "Parent" not in headers:
        return [], ["docs/CURRENT_TASK.md: governance Limbs ledger lacks Parent"]
    parent_index = headers.index("Parent")
    parents: List[str] = []
    for line in table_lines[2:]:
        cells = [cell.strip().strip("`") for cell in line.strip("|").split("|")]
        if len(cells) != len(headers):
            errors.append("docs/CURRENT_TASK.md: malformed governance Limbs row")
            continue
        parents.append(cells[parent_index])
    if not parents:
        errors.append("docs/CURRENT_TASK.md: governance Limbs ledger has no task rows")
    return parents, errors


def check_repo_contract(repo_root: Path) -> List[str]:
    repo_root = repo_root.resolve()
    errors: List[str] = []

    config_path = repo_root / ".codex" / "config.toml"
    try:
        config = parse_contract_toml(config_path)
    except (OSError, ContractTomlError) as exc:
        return [str(exc)]

    for agent_name in ("brain", "limbs"):
        table_name = f"agents.{agent_name}"
        table = config.get(table_name)
        if table is None:
            errors.append(f"{config_path}: missing [{table_name}]")
            continue
        config_file = table.get("config_file")
        if not isinstance(config_file, str):
            errors.append(f"{config_path}: {table_name}.config_file must be a string")
            continue
        expected_config_file = f"./agents/{agent_name}.toml"
        if config_file != expected_config_file:
            errors.append(
                f"{config_path}: {table_name}.config_file must be {expected_config_file}"
            )
        agent_path = (config_path.parent / config_file).resolve()
        if not agent_path.is_file():
            errors.append(f"{config_path}: missing resolved Agent config {agent_path}")
            continue
        try:
            agent = parse_contract_toml(agent_path).get("", {})
        except (OSError, ContractTomlError) as exc:
            errors.append(str(exc))
            continue
        required_fields = {
            "name",
            "description",
            "model",
            "model_reasoning_effort",
            "sandbox_mode",
            "developer_instructions",
        }
        missing = sorted(required_fields.difference(agent))
        if missing:
            errors.append(f"{agent_path}: missing fields {', '.join(missing)}")
        if agent.get("name") != agent_name:
            errors.append(f"{agent_path}: name must be {agent_name}")
        if agent.get("sandbox_mode") != "workspace-write":
            errors.append(f"{agent_path}: sandbox_mode must be workspace-write")
        if agent_name == "brain":
            if agent.get("model") != "gpt-5.6-sol":
                errors.append(f"{agent_path}: Brain model must be gpt-5.6-sol")
            if agent.get("model_reasoning_effort") != "medium":
                errors.append(f"{agent_path}: Brain reasoning must be medium")
            instructions = str(agent.get("developer_instructions", ""))
            for marker in (
                "spawn_agent",
                "idle Limbs 使用 followup_task",
                "running Limbs 使用 send_message",
                "你永远不得调用 create_thread",
                "由 root 调用 create_thread",
                "跨层根因分析",
                "三份活动文档",
            ):
                if marker not in instructions:
                    errors.append(f"{agent_path}: missing Brain duty marker {marker}")
            if "禁止使用 create_thread，除非" in instructions:
                errors.append(f"{agent_path}: Brain create_thread exception is forbidden")
        else:
            if agent.get("model") != "gpt-5.6-luna":
                errors.append(f"{agent_path}: Limbs model must be gpt-5.6-luna")
            if agent.get("model_reasoning_effort") != "high":
                errors.append(f"{agent_path}: Limbs reasoning must be high")
            instructions = str(agent.get("developer_instructions", ""))
            for marker in ("PARENT_BRAIN", "ANR", "普通编译"):
                if marker not in instructions:
                    errors.append(f"{agent_path}: missing Limbs duty marker {marker}")

    errors.extend(_check_skill(repo_root / ".codex" / "skills" / "begin", "begin"))
    errors.extend(
        _check_skill(
            repo_root / ".codex" / "skills" / "lyriccaptioner-brain",
            "lyriccaptioner-brain",
        )
    )

    managed_contents: Dict[str, str] = {}
    for relative in MANAGED_TEXT_FILES:
        path = repo_root / relative
        try:
            managed_contents[relative] = _read(path)
        except OSError as exc:
            errors.append(str(exc))
    combined = "\n".join(managed_contents.values())
    if re.search(r"\bReviewer\b", combined, re.IGNORECASE):
        errors.append("managed governance scope contains formal Reviewer residue")
    if "禁止使用 create_thread，除非" in combined:
        errors.append("managed governance scope contains a Brain create_thread exception")
    for ambiguous_delivery in (
        "followup_task 或 send_message",
        "`followup_task` 或 `send_message`",
    ):
        if ambiguous_delivery in combined:
            errors.append(
                "managed governance scope conflates idle and running Brain delivery"
            )
    for obsolete in (
        "当前主对话的逻辑角色是 **Brain**",
        "Brain 是主对话",
        "Brain 是主对话、",
    ):
        if obsolete in combined:
            errors.append(f"managed governance scope contains obsolete role text: {obsolete}")

    required_governance_markers = (
        "Primary/root coordinator shell -> unique internal Brain -> Brain-owned Limbs",
        "root 绝不直接",
        "path 存在但不 live",
        "Brain 永远不得调用",
        "ANR、OOM、并发、架构、数据一致性和安全",
        "REPO_CONTRACT_ENFORCED",
    )
    for marker in required_governance_markers:
        if marker not in combined:
            errors.append(f"managed governance scope missing contract marker: {marker}")

    begin_text = managed_contents.get(".codex/skills/begin/SKILL.md", "")
    brain_text = managed_contents.get(
        ".codex/skills/lyriccaptioner-brain/SKILL.md", ""
    )
    for marker in (
        "spawn_agent",
        "followup_task",
        "send_message",
        "create_thread",
        "Brain 窗口",
        "runtime liveness",
        "path 存在但不 live",
    ):
        if marker not in begin_text:
            errors.append(f"begin skill missing route marker: {marker}")
    for marker in (
        "spawn_agent",
        "followup_task",
        "send_message",
        "Brain 永远不得调用 `create_thread`",
        "结构化返回 root",
        "idle Limbs 使用",
        "running Limbs 使用",
    ):
        if marker not in brain_text:
            errors.append(f"Brain skill missing route marker: {marker}")

    current_path = repo_root / "docs" / "CURRENT_TASK.md"
    state_path = repo_root / "docs" / "PROJECT_STATE.md"
    try:
        current_text = _read(current_path)
        state_text = _read(state_path)
        current_brain, current_errors = _active_brain_from(current_text, current_path)
        state_brain, state_errors = _active_brain_from(state_text, state_path)
        errors.extend(current_errors)
        errors.extend(state_errors)
        if current_brain and state_brain and current_brain != state_brain:
            errors.append("activity documents disagree on Active Brain canonical path")
        parents, ledger_errors = _governance_ledger_parents(current_text)
        errors.extend(ledger_errors)
        for parent in parents:
            if parent == "/root":
                errors.append("active governance Limbs parent must not be /root")
            elif not current_brain or parent != current_brain:
                errors.append(
                    "active governance Limbs parent must equal Active Brain canonical path"
                )
    except OSError as exc:
        errors.append(str(exc))
    return errors


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repo",
        type=Path,
        default=Path(__file__).resolve().parents[1],
        help="LyricCaptioner repository root",
    )
    args = parser.parse_args(argv)
    errors = check_repo_contract(args.repo)
    if errors:
        for error in errors:
            print(f"CONTRACT_ERROR: {error}", file=sys.stderr)
        return 1
    print(REPO_CONTRACT_ENFORCED)
    print(f"PLATFORM_BOUNDARY: {PLATFORM_BOUNDARY}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
