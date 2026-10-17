ALTER TABLE public.lecture ADD COLUMN professor_name varchar(500);

UPDATE public.lecture l
SET professor_name = p.name
FROM public.professor p
WHERE l.professor_id = p.id;

ALTER TABLE public.lecture
    ALTER COLUMN professor_name SET NOT NULL,
    ADD CONSTRAINT ck_lecture_professor_name_not_blank CHECK (length(btrim(professor_name)) > 0);

ALTER TABLE public.lecture DROP COLUMN professor_id;
DROP TABLE public.professor;
