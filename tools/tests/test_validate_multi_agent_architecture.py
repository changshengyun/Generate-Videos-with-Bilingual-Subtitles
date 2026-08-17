import shutil
import sys
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
TOOLS_DIR = REPO_ROOT / "tools"
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

import validate_multi_agent_architecture as validator


FIXTURE_FILES = tuple(validator.MANAGED_TEXT_FILES) + (
    "docs/CURRENT_TASK.md",
    "docs/PROJECT_STATE.md",
)


class RepositoryContractTest(unittest.TestCase):
    def make_fixture(self):
        temporary = tempfile.TemporaryDirectory()
        root = Path(temporary.name)
        for relative in FIXTURE_FILES:
            source = REPO_ROOT / relative
            target = root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(str(source), str(target))
        return temporary, root

    def assert_contract_error(self, mutate, fragment):
        temporary, root = self.make_fixture()
        self.addCleanup(temporary.cleanup)
        mutate(root)
        errors = validator.check_repo_contract(root)
        self.assertTrue(errors)
        self.assertTrue(
            any(fragment in error for error in errors),
            msg="Expected %r in %r" % (fragment, errors),
        )

    @staticmethod
    def replace_governance_parent(root, replacement):
        path = root / "docs/CURRENT_TASK.md"
        text = path.read_text(encoding="utf-8")
        section_start = text.index("## GOV-MULTIAGENT-001 Limbs 账本")
        prefix, section = text[:section_start], text[section_start:]
        active_match = validator.ACTIVE_BRAIN_RE.search(text)
        if not active_match:
            raise AssertionError("governance fixture is missing its active Brain")
        original = "`%s`" % active_match.group("path")
        if original not in section:
            raise AssertionError("governance fixture is missing its active parent")
        section = section.replace(original, "`%s`" % replacement, 1)
        path.write_text(prefix + section, encoding="utf-8")

    def test_current_repository_contract_passes(self):
        self.assertEqual([], validator.check_repo_contract(REPO_ROOT))

    def test_missing_brain_registration_fails(self):
        def mutate(root):
            path = root / ".codex/config.toml"
            text = path.read_text(encoding="utf-8")
            start = text.index("[agents.brain]")
            end = text.index("[agents.limbs]")
            path.write_text(text[:start] + text[end:], encoding="utf-8")

        self.assert_contract_error(mutate, "missing [agents.brain]")

    def test_reviewer_residue_fails(self):
        def mutate(root):
            path = root / ".codex/agents/limbs.toml"
            path.write_text(
                path.read_text(encoding="utf-8") + "\n# Reviewer role\n",
                encoding="utf-8",
            )

        self.assert_contract_error(mutate, "Reviewer residue")

    def test_missing_active_brain_path_fails(self):
        def mutate(root):
            path = root / "docs/CURRENT_TASK.md"
            lines = path.read_text(encoding="utf-8").splitlines()
            path.write_text(
                "\n".join(
                    line for line in lines if "Active Brain canonical path:" not in line
                )
                + "\n",
                encoding="utf-8",
            )

        self.assert_contract_error(mutate, "expected exactly one Active Brain")

    def test_parent_root_fails(self):
        def mutate(root):
            self.replace_governance_parent(root, "/root")

        self.assert_contract_error(mutate, "parent must not be /root")

    def test_wrong_parent_fails(self):
        def mutate(root):
            self.replace_governance_parent(root, "/root/different_brain")

        self.assert_contract_error(mutate, "parent must equal")

    def test_begin_missing_route_contract_fails(self):
        def mutate(root):
            path = root / ".codex/skills/begin/SKILL.md"
            text = path.read_text(encoding="utf-8").replace(
                "- root 绝不直接 `spawn_agent` 创建 Limbs。",
                "- root may dispatch execution directly.",
            )
            path.write_text(text, encoding="utf-8")

        self.assert_contract_error(mutate, "root 绝不直接")

    def test_brain_create_thread_exception_fails(self):
        def mutate(root):
            path = root / ".codex/agents/brain.toml"
            text = path.read_text(encoding="utf-8")
            text = text.replace(
                "你永远不得调用 create_thread。用户明确要求",
                "禁止使用 create_thread，除非用户明确要求",
            )
            path.write_text(text, encoding="utf-8")

        self.assert_contract_error(mutate, "Brain create_thread exception is forbidden")

    def test_ambiguous_brain_delivery_tools_fail(self):
        def mutate(root):
            path = root / ".codex/skills/begin/SKILL.md"
            text = path.read_text(encoding="utf-8")
            text += "\n已有 Brain 使用 `followup_task` 或 `send_message` 复用。\n"
            path.write_text(text, encoding="utf-8")

        self.assert_contract_error(mutate, "conflates idle and running Brain delivery")

    def test_unterminated_agent_multiline_string_fails(self):
        temporary, root = self.make_fixture()
        self.addCleanup(temporary.cleanup)
        path = root / ".codex/agents/brain.toml"
        text = path.read_text(encoding="utf-8")
        self.assertTrue(text.endswith('"""\n'))
        path.write_text(text[:-4], encoding="utf-8")
        errors = validator.check_repo_contract(root)
        self.assertTrue(any("unterminated multiline string" in error for error in errors))


class RuntimeTopologyTest(unittest.TestCase):
    ACTIVE = "/root/brain_architecture_audit"

    def test_root_direct_limbs_is_rejected(self):
        with self.assertRaises(validator.ContractViolation):
            validator.validate_runtime_topology(
                "/root", "SPAWN_LIMBS", self.ACTIVE, self.ACTIVE
            )

    def test_active_brain_owned_limbs_is_allowed(self):
        self.assertTrue(
            validator.validate_runtime_topology(
                self.ACTIVE, "SPAWN_LIMBS", self.ACTIVE, self.ACTIVE
            )
        )

    def test_wrong_parent_is_rejected(self):
        with self.assertRaises(validator.ContractViolation):
            validator.validate_runtime_topology(
                self.ACTIVE, "SPAWN_LIMBS", self.ACTIVE, "/root"
            )

    def test_cold_start_without_active_brain_spawns_internal(self):
        decision = validator.cold_start_route(None, runtime_live=False)
        self.assertEqual(validator.SPAWN_INTERNAL_BRAIN, decision.action)
        self.assertEqual("lyriccaptioner_brain", decision.task_name)
        self.assertFalse(decision.reconciliation_required)

    def test_cold_start_with_active_brain_reuses_internal(self):
        decision = validator.cold_start_route(self.ACTIVE, runtime_live=True)
        self.assertEqual(
            validator.REUSE_INTERNAL_BRAIN,
            decision.action,
        )
        self.assertIsNone(decision.task_name)
        self.assertFalse(decision.reconciliation_required)

    def test_persisted_but_not_live_spawns_replacement(self):
        decision = validator.cold_start_route(self.ACTIVE, runtime_live=False)
        self.assertEqual(validator.SPAWN_INTERNAL_BRAIN, decision.action)
        self.assertEqual("brain_architecture_audit", decision.task_name)
        self.assertTrue(decision.reconciliation_required)

    def test_invalid_active_brain_path_is_rejected(self):
        with self.assertRaises(validator.ContractViolation):
            validator.cold_start_route("/root", runtime_live=False)

    def test_live_without_persisted_path_is_rejected(self):
        with self.assertRaises(validator.ContractViolation):
            validator.cold_start_route(None, runtime_live=True)

    def test_idle_and_running_delivery_tools_are_distinct(self):
        self.assertEqual("followup_task", validator.brain_delivery_tool(True, False))
        self.assertEqual("send_message", validator.brain_delivery_tool(True, True))
        with self.assertRaises(validator.ContractViolation):
            validator.brain_delivery_tool(False, False)

    def test_only_root_can_create_explicit_external_thread(self):
        self.assertTrue(
            validator.validate_runtime_topology(
                "/root",
                validator.CREATE_EXTERNAL_THREAD,
                self.ACTIVE,
                explicit_external_request=True,
            )
        )
        with self.assertRaises(validator.ContractViolation):
            validator.validate_runtime_topology(
                self.ACTIVE,
                validator.CREATE_EXTERNAL_THREAD,
                self.ACTIVE,
                explicit_external_request=True,
            )


class NaturalLanguageRouterTest(unittest.TestCase):
    ACTIVE = "/root/brain_architecture_audit"

    def test_internal_brain_phrases_spawn_when_absent(self):
        for prompt in ("开一个 Brain", "启动 Brain", "恢复 Brain", "开一个 Brain 窗口"):
            with self.subTest(prompt=prompt):
                self.assertEqual(
                    validator.SPAWN_INTERNAL_BRAIN,
                    validator.route_natural_language(
                        prompt, persisted_brain_path=None, runtime_live=False
                    ).action,
                )

    def test_internal_brain_phrases_reuse_when_present(self):
        for prompt in ("开一个 Brain", "启动 Brain", "恢复 Brain", "Brain 窗口"):
            with self.subTest(prompt=prompt):
                self.assertEqual(
                    validator.REUSE_INTERNAL_BRAIN,
                    validator.route_natural_language(
                        prompt,
                        persisted_brain_path=self.ACTIVE,
                        runtime_live=True,
                    ).action,
                )

    def test_natural_router_does_not_assume_persisted_brain_is_live(self):
        decision = validator.route_natural_language(
            "恢复 Brain",
            persisted_brain_path=self.ACTIVE,
            runtime_live=False,
        )
        self.assertEqual(validator.SPAWN_INTERNAL_BRAIN, decision.action)
        self.assertEqual("brain_architecture_audit", decision.task_name)
        self.assertTrue(decision.reconciliation_required)

    def test_only_explicit_codex_external_phrases_create_thread(self):
        for prompt in (
            "新建独立 Codex 窗口",
            "创建独立 Codex 侧边栏任务",
            "帮我开一个独立 Codex 任务",
        ):
            with self.subTest(prompt=prompt):
                self.assertEqual(
                    validator.CREATE_EXTERNAL_THREAD,
                    validator.route_natural_language(
                        prompt,
                        persisted_brain_path=self.ACTIVE,
                        runtime_live=True,
                    ).action,
                )

    def test_ambiguous_window_never_creates_external_thread(self):
        self.assertNotEqual(
            validator.CREATE_EXTERNAL_THREAD,
            validator.route_natural_language(
                "请开一个 Brain 窗口",
                persisted_brain_path=self.ACTIVE,
                runtime_live=True,
            ).action,
        )


if __name__ == "__main__":
    unittest.main()
