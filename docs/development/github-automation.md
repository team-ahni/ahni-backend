# GitHub 자동화

## 동작

- `feat/**`, `fix/**`, `docs/**`, `refactor/**`, `chore/**` 브랜치에 푸시하면 `auto-pr.yml`이 `main` 대상 PR을 자동 생성합니다.
- 동일한 브랜치에 열린 PR이 있으면 새 PR을 만들지 않습니다.
- PR은 push 사용자별 PAT로 생성합니다. 토큰 소유자가 push 사용자와 다르거나 Secret이 없으면 생성·갱신을 중단합니다.
- PR과 대상 브랜치에 `ci.yml`이 실행되며 `./scripts/verify`가 통과해야 합니다.

## GitHub에서 한 번만 설정할 항목

### 1. Actions Secret

저장소 `Settings → Secrets and variables → Actions`에 작업자별 `GH_PAT_<사용자 ID 대문자>` Secret을 등록합니다. 하이픈은 밑줄로 바꿉니다. 예: `GH_PAT_NIMUSEEL`. 기존 공용 `GH_PAT`는 사용하지 않습니다. [작업자별 설정 안내](automatic-pr.md)를 따릅니다.

해당 사용자가 대상 저장소에 접근할 수 있는 fine-grained Personal Access Token을 권장하며 다음 권한을 부여합니다. 외부 협업자 제한 등 지원 범위는 위 설정 안내에서 확인합니다.

- Contents: Read-only
- Pull requests: Read and write
- Metadata: Read-only

토큰은 PR 생성 주체를 사용자 계정으로 만들기 위한 용도로만 사용하며, 워크플로 로그에 출력하지 않습니다.

## Copilot 리뷰

Copilot 자동 리뷰는 사용하지 않습니다(2026-11 제거). 워크플로에서 리뷰를 요청하지 않으며, 저장소 Settings의 Copilot automatic code review 규칙도 켜지 않습니다. 리뷰는 사람이 합니다.
