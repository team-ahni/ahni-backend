ALTER TABLE public.graduation_requirement
    ADD COLUMN min_foundation_credit numeric(5,1) NOT NULL DEFAULT 0,
    ADD COLUMN evaluation_complete boolean NOT NULL DEFAULT true,
    ADD COLUMN policy_details jsonb;
ALTER TABLE public.graduation_requirement
    ADD CONSTRAINT ck_graduation_foundation_credit CHECK (min_foundation_credit BETWEEN 0 AND 999.9);
COMMENT ON COLUMN public.graduation_requirement.min_foundation_credit IS '전공과 별도로 요구되는 전공기초 최소학점';
COMMENT ON COLUMN public.graduation_requirement.evaluation_complete IS '선택·대체·면제 등 모든 조건에 대한 자동 판정 지원 여부';
COMMENT ON COLUMN public.graduation_requirement.policy_details IS '공식 출처, 학번별 원 과목, 선택·대체·미확인 규칙의 입력 기록';
