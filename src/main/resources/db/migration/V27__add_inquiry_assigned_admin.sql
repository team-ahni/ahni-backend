ALTER TABLE public.inquiry
    ADD COLUMN assigned_admin_id bigint,
    ADD CONSTRAINT fk_inquiry_assigned_admin
        FOREIGN KEY (assigned_admin_id) REFERENCES public.admin (id);
