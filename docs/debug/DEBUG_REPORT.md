# V3-ASR-DIAG-001 Base Native Debug Report

## Failure and scope

- Expected: fixed WAV should yield raw lyric segments rather than a single music marker.
- Previous actual symptom: recognition could collapse to `[MUSIC]`/one raw segment after `whisper_full=0`.
- Scope frozen by user: base only; small cancelled; no UI, post-processing, AI, translation, threshold, architecture, or product-ASR changes.
- Device: `fcf4b0cb / 25098PN5AC / API 36`.
- App commit: `50fd1407ad9e63c34b27292bf36adc81db7b062e`.

## Reproduction

```text
adb -s fcf4b0cb shell am instrument -w -r \
  -e mode run \
  -e label base \
  -e model /data/local/tmp/v3-asr-diag-base.en.bin \
  -e wav /data/user/0/com.example.lyriccaptioner/files/asr-diagnostics/fixed-input.wav \
  com.example.lyriccaptioner.test/com.example.lyriccaptioner.AsrDiagnosticInstrumentation
```

## Fixed identities and configuration

- Native runtime: `whisper.cpp 1.9.1 / f049fff`; GGML `0.15.1`.
- Model SHA256: `a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002`.
- WAV SHA256: `cd76904fc36ac08de32da432a4a6c14c48bf34f267c082cb74d6a1ec5c692d1d`.
- WAV: `31,602 ms / 505,638 samples / 16 kHz / mono / PCM16`.
- Params: Greedy, fresh context, context reuse false, `no_context=true`, `language=auto`, translate false, 4 threads.

## Execution

- Context init: `97 ms`.
- `whisper_full` return: `0`.
- Inference: `8,580 ms`.
- Segment count: `9`.
- Context lifecycle: created once and freed immediately after inference.
- Raw JSON: device `files/asr-diagnostics/base.json`, 5,360 bytes, SHA256 `69e44af46a24299921e9ad420dd13f4041419870226ffe33993aa0e1646762fc`.

## Segment and token evidence

Average token probability includes all finite tokens, including special timestamp tokens.

| Seg | ms | Text | no_speech | avg token | Tokens: `id text probability` |
|---:|---:|---|---:|---:|---|
| 0 | 0–2800 | `I have to live without you` | 0.475166 | 0.807502 | `50363 [_BEG_] .918818`; `314 I .787300`; `423 have .830198`; `284 to .994059`; `2107 live .930704`; `1231 without .990080`; `345 you .993735`; `50503 [_TT_140] .015124` |
| 1 | 2800–7000 | `Nobody could, I need to be around you` | 0.475166 | 0.801631 | `15658 Nobody .928809`; `714 could .990994`; `11 , .475024`; `314 I .931170`; `761 need .978696`; `284 to .997885`; `307 be .976338`; `1088 around .693950`; `345 you .979499`; `50713 [_TT_350] .063942` |
| 2 | 7000–10600 | `Watching you, no one else can love you` | 0.475166 | 0.847775 | `36110 Watching .907871`; `345 you .996535`; `11 , .621691`; `645 no .965037`; `530 one .956590`; `2073 else .994179`; `460 can .954348`; `1842 love .993050`; `345 you .982266`; `50893 [_TT_530] .106186` |
| 3 | 10600–12600 | `Like I do` | 0.475166 | 0.712028 | `4525 Like .966225`; `314 I .838338`; `466 do .990558`; `50993 [_TT_630] .052993` |
| 4 | 12600–17000 | `Healing and I'm creeping up on you` | 0.475166 | 0.598653 | `22508 Healing .308874`; `290 and .145394`; `314 I .819088`; `1101 'm .970995`; `38598 creeping .679826`; `510 up .747923`; `319 on .417294`; `345 you .997632`; `51213 [_TT_850] .300853` |
| 5 | 17000–20000 | `I know that it won't be right` | 0.475166 | 0.861705 | `314 I .998951`; `760 know .994634`; `326 that .983648`; `340 it .901328`; `1839 won .686971`; `470 't .999493`; `307 be .998251`; `826 right .979072`; `51363 [_TT_1000] .212993` |
| 6 | 20000–25000 | `If I stay all night to be among you` | 0.475166 | 0.768094 | `1002 If .640142`; `314 I .996200`; `2652 stay .954818`; `477 all .624054`; `1755 night .993519`; `284 to .401176`; `307 be .686590`; `1871 among .768641`; `345 you .996858`; `51613 [_TT_1250] .618945` |
| 7 | 25000–29000 | `Creeping my own you` | 0.475166 | 0.659606 | `5844 Cre .598658`; `7213 eping .824640`; `616 my .440416`; `898 own .936109`; `345 you .607266`; `51813 [_TT_1450] .550549` |
| 8 | 30000–31400 | `(upbeat music)` | 0.881964 | 0.671243 | `50363 [_BEG_] .764580`; `357 ( .526213`; `929 up .620827`; `12945 beat .997587`; `2647 music .765982`; `8 ) .976471`; `50433 [_TT_70] .047043` |

## Hypotheses

| Hypothesis | Evidence | Status |
|---|---|---|
| A. Context reuse | Fresh context run recovered eight lyric segments. | Consistent, not isolated from `no_context`. |
| B. Native/build environment | The actual `1.9.1/f049fff` binary returned 9 coherent segments and valid token probabilities. | Global Native decoder failure contradicted. |
| C. Whisper parameters | `no_context=true` was active in the recovered run. | Consistent, not isolated from fresh context. |
| D. Insufficient data | Fresh context and `no_context=true` changed together. | Selected conclusion for A versus C. |

## Conclusion

The decoder itself emitted `(upbeat music)` only for the final high-no-speech window. It was not introduced by Kotlin subtitle post-processing. The current Native artifact can decode the fixed WAV normally. This run cannot uniquely distinguish Context reuse from the `no_context` parameter; no product fix is authorized or applied.
