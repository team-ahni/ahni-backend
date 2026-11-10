# 팀원 자동 PR 설정 가이드

본인 GitHub 계정으로 PAT를 발급하고 저장소에 등록하면, push할 때 본인 명의의 PR이 자동으로 생성됩니다.

## 1. 본인 계정에서 PAT 발급

PAT(Personal Access Token)는 GitHub API가 본인 계정으로 작업하도록 허용하는 인증 토큰입니다.

### Fine-grained PAT 발급

[Fine-grained PAT 발급 페이지](https://github.com/settings/personal-access-tokens/new)를 엽니다.

메뉴 경로: **개인 Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token**

1. Token name에 용도를 입력합니다. 예: `ahni-auto-pr`
2. Expiration에 만료일을 설정합니다.
3. Resource owner에 조직 `team-ahni`를 선택합니다. 개인 계정을 고르면 조직 저장소에 접근하지 못해 403이 발생합니다.
4. Repository access에서 **Only select repositories**를 선택하고 사용할 AHNI 저장소를 지정합니다.
5. Repository permissions를 설정합니다.
   - Contents: **Read-only**
   - Pull requests: **Read and write**
   - Metadata: **Read-only** (기본 권한)
6. **Generate token**을 누르고 발급된 값을 복사합니다.

팀원은 본인 계정의 Resource owner와 Selected repositories 목록에서 대상 저장소를 선택할 수 있는지 확인합니다.
선택할 수 없다면 저장소 관리자에게 Fine-grained PAT로 접근 가능한 저장소 소유 구조와 권한 설정을 확인한 뒤 진행합니다.
발급 직후 토큰은 조직 승인 대기 상태일 수 있습니다. 조직 관리자가 [승인 요청 목록](https://github.com/organizations/team-ahni/settings/personal-access-token-requests)에서 승인해야 동작합니다.
토큰은 본인 계정으로 발급하고, 복사한 값은 아래 Repository secret 입력란에만 등록합니다.

토큰 종류와 협업자 제한: [GitHub PAT 공식 안내](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens)

## 2. Secret 이름 정하기

이름은 `GH_PAT_{memberId}` 형식으로 만듭니다.
`memberId` 자리에 **개인 GitHub ID를 대문자로 바꾸고, 하이픈을 밑줄로 바꾼 값**을 넣습니다.

**GitHub ID는 현재 사용 중인 그대로 유지하고, Secret 이름만 대문자로 작성합니다.**

| 개인 GitHub ID | 등록할 Secret 이름    |
| -------------- | --------------------- |
| `nimuseel`     | `GH_PAT_NIMUSEEL`     |
| `kimdev`       | `GH_PAT_KIMDEV`       |
| `hong-gildong` | `GH_PAT_HONG_GILDONG` |

## 3. 사용하는 저장소마다 Secret 등록

아래에서 본인이 작업할 저장소의 설정 페이지를 엽니다.

- [백엔드 Secret 설정](https://github.com/team-ahni/ahni-backend/settings/secrets/actions)
- [모바일 Secret 설정](https://github.com/team-ahni/ahni-frontend/settings/secrets/actions)
- [어드민 Secret 설정](https://github.com/team-ahni/ahni-admin/settings/secrets/actions)

메뉴 경로: **저장소 Settings → Secrets and variables → Actions → Secrets → New repository secret**

1. **Name**에 2단계에서 만든 이름을 입력합니다. 예: `GH_PAT_KIMDEV`
2. **Secret**에 1단계에서 발급한 본인 PAT 값을 붙여 넣습니다.
3. **Add secret**을 누릅니다.
4. 다른 저장소에서도 작업한다면 해당 저장소에 반복해서 등록합니다.

등록 메뉴에 접근하기 어려우면 저장소 관리자에게 등록 절차를 요청합니다.
등록 방법: [GitHub Secrets 공식 안내](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)

## 4. Push 후 실행 확인

1. 자동 PR 설정이 반영된 코드에서 본인 작업용 `feat/`, `fix/`, `docs/`, `refactor/`, `chore/` 브랜치를 사용합니다.
2. 본인 GitHub 계정으로 변경을 push합니다.
3. 저장소 **Actions → Create or update PR**에서 실행 결과를 확인합니다.
4. **Pull requests**에서 새 PR 작성자가 본인인지 확인합니다.
5. Secret 등록 전에 실패한 실행은 등록 후 **Re-run failed jobs**로 다시 실행합니다. 이때 최초 push한 계정의 Secret을 등록합니다.
6. 토큰 만료 전 새 PAT를 발급하고 해당 Repository secret 값을 갱신합니다.

재실행 방법: [GitHub Actions 재실행 안내](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/re-run-workflows-and-jobs)
