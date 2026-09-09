--
-- PostgreSQL database dump
--


-- Schema dump for zuo_ai_agent
-- Dumped by pg_dump version 17.10 (Homebrew)













--
-- Name: hstore; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS hstore WITH SCHEMA public;


--
-- Name: EXTENSION hstore; Type: COMMENT; Schema: -; Owner: -
--



--
-- Name: uuid-ossp; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;


--
-- Name: EXTENSION "uuid-ossp"; Type: COMMENT; Schema: -; Owner: -
--



--
-- Name: vector; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;


--
-- Name: EXTENSION vector; Type: COMMENT; Schema: -; Owner: -
--



--
-- Name: update_knowledge_document_search_vector(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_knowledge_document_search_vector() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.search_vector := to_tsvector('simple', COALESCE(NEW.doc_name, ''));
    RETURN NEW;
END;
$$;






--
-- Name: t_alert_record; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_alert_record (
    id bigint NOT NULL,
    rule_id bigint,
    alert_level character varying(16),
    alert_message text,
    metric_value double precision,
    status character varying(16) DEFAULT 'FIRED'::character varying,
    fire_time timestamp without time zone,
    resolve_time timestamp without time zone
);


--
-- Name: t_alert_rule; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_alert_rule (
    id bigint NOT NULL,
    rule_name character varying(128),
    metric character varying(64),
    condition character varying(16),
    threshold double precision,
    window_minutes integer DEFAULT 5,
    notify_channels character varying(256),
    enabled boolean DEFAULT true
);


--
-- Name: t_app_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_app_log (
    id bigint NOT NULL,
    service_name character varying(64),
    host_name character varying(128),
    trace_id character varying(64),
    log_level character varying(8),
    logger_name character varying(256),
    thread_name character varying(128),
    message text,
    stack_trace text,
    log_ts timestamp without time zone,
    create_time timestamp without time zone DEFAULT now(),
    log_type character varying(20) DEFAULT 'normal'::character varying,
    span_id character varying(64),
    parent_trace_id character varying(64),
    event_type character varying(100),
    node_id bigint,
    metadata text
);


--
-- Name: t_approval; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_approval (
    id bigint NOT NULL,
    apply_type character varying(32),
    applicant_id bigint,
    target_type character varying(32),
    target_id bigint,
    target_name character varying(256),
    approver_type character varying(32),
    approver_id bigint,
    status character varying(16) DEFAULT 'PENDING'::character varying,
    reject_reason character varying(512),
    apply_time timestamp without time zone,
    approve_time timestamp without time zone,
    tenant_id bigint,
    status_updated_at timestamp without time zone
);


--
-- Name: t_audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_audit_log (
    id bigint NOT NULL,
    user_id bigint,
    action character varying(32),
    target_type character varying(32),
    target_id bigint,
    detail text,
    ip_address character varying(64),
    user_agent character varying(256),
    create_time timestamp without time zone DEFAULT now(),
    tenant_id bigint
);


--
-- Name: t_chat_memory; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_chat_memory (
    id bigint NOT NULL,
    conversation_id character varying(64) NOT NULL,
    role character varying(16) NOT NULL,
    content text NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: t_chat_memory_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_chat_memory_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_chat_memory_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_chat_memory_id_seq OWNED BY public.t_chat_memory.id;


--
-- Name: t_chat_message_compression; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_chat_message_compression (
    id bigint NOT NULL,
    conversation_id character varying(64) NOT NULL,
    summary_content text NOT NULL,
    source_msg_ids text,
    source_count integer DEFAULT 0,
    create_time timestamp without time zone DEFAULT now()
);


--
-- Name: t_chat_message_raw; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_chat_message_raw (
    id bigint NOT NULL,
    msg_id character varying(64) NOT NULL,
    conversation_id character varying(64) NOT NULL,
    role character varying(16) NOT NULL,
    content text NOT NULL,
    create_time timestamp without time zone DEFAULT now(),
    tenant_id bigint,
    user_id bigint
);


--
-- Name: t_conversation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_conversation (
    id character varying(64) NOT NULL,
    title character varying(256),
    tenant_id bigint,
    user_id bigint,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    deleted smallint DEFAULT 0
);


--
-- Name: t_data_replay_request; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_data_replay_request (
    id bigint NOT NULL,
    trace_id character varying(64) NOT NULL,
    question_text text NOT NULL,
    source_conversation_id character varying(64),
    status character varying(20) DEFAULT 'PENDING'::character varying,
    approved_by bigint,
    approved_time timestamp without time zone,
    target_question_id bigint,
    create_user_id bigint,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    tenant_id bigint
);


--
-- Name: t_data_replay_request_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_data_replay_request_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_data_replay_request_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_data_replay_request_id_seq OWNED BY public.t_data_replay_request.id;


--
-- Name: t_db_access; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_db_access (
    id bigint NOT NULL,
    read_count bigint DEFAULT 0,
    write_count bigint DEFAULT 0,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: t_db_access_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_db_access_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_db_access_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_db_access_id_seq OWNED BY public.t_db_access.id;


--
-- Name: t_db_qps; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_db_qps (
    id bigint NOT NULL,
    query_count bigint DEFAULT 0,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: t_db_qps_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_db_qps_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_db_qps_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_db_qps_id_seq OWNED BY public.t_db_qps.id;


--
-- Name: t_db_slowquery; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_db_slowquery (
    id bigint NOT NULL,
    db_name character varying(128),
    query_sql text,
    execution_time bigint DEFAULT 0,
    lock_time bigint DEFAULT 0,
    rows_examined bigint DEFAULT 0,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: t_db_slowquery_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_db_slowquery_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_db_slowquery_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_db_slowquery_id_seq OWNED BY public.t_db_slowquery.id;


--
-- Name: t_event; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_event (
    id bigint NOT NULL,
    event_type character varying(32) NOT NULL,
    source character varying(128),
    message text,
    service_name character varying(64),
    host character varying(128),
    event_time timestamp without time zone NOT NULL,
    metadata text,
    create_time timestamp without time zone DEFAULT now()
);


--
-- Name: t_gray_release_plan; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_gray_release_plan (
    id bigint NOT NULL,
    plan_name character varying(100) NOT NULL,
    component_type character varying(50) NOT NULL,
    component_id bigint,
    from_version_id bigint,
    to_version_id bigint,
    gray_ratio double precision DEFAULT 0.1,
    gray_mode character varying(20) DEFAULT 'PERCENT'::character varying,
    gray_user_ids text,
    gray_duration_hours integer DEFAULT 24,
    status character varying(20) DEFAULT 'DRAFT'::character varying,
    approved_by bigint,
    approved_time timestamp without time zone,
    gray_metrics_snapshot text,
    change_log character varying(500),
    create_user_id bigint,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    finish_time timestamp without time zone,
    tenant_id bigint
);


--
-- Name: t_gray_release_plan_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_gray_release_plan_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_gray_release_plan_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_gray_release_plan_id_seq OWNED BY public.t_gray_release_plan.id;


--
-- Name: t_gray_release_plan_model; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_gray_release_plan_model (
    id bigint NOT NULL,
    plan_id bigint NOT NULL,
    model_config_id bigint NOT NULL,
    from_is_active smallint DEFAULT 0,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: t_gray_release_plan_model_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_gray_release_plan_model_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_gray_release_plan_model_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_gray_release_plan_model_id_seq OWNED BY public.t_gray_release_plan_model.id;


--
-- Name: t_heartbeat; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_heartbeat (
    id bigint NOT NULL,
    service_name character varying(64) NOT NULL,
    host character varying(128) NOT NULL,
    status character varying(16) DEFAULT 'HEALTHY'::character varying,
    cpu_usage double precision,
    memory_usage double precision,
    active_threads integer,
    gc_count bigint,
    heartbeat_time timestamp without time zone NOT NULL,
    create_time timestamp without time zone DEFAULT now()
);


--
-- Name: t_ingestion_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_ingestion_log (
    id bigint NOT NULL,
    doc_id bigint NOT NULL,
    kb_id bigint NOT NULL,
    stage character varying(32) NOT NULL,
    status character varying(16) NOT NULL,
    duration_ms integer,
    total_duration_ms integer,
    chunks_count integer DEFAULT 0,
    chunks_success integer DEFAULT 0,
    error_message text,
    file_type character varying(64),
    file_size bigint,
    text_length integer,
    strategy character varying(32),
    created_at timestamp without time zone DEFAULT now(),
    tenant_id bigint DEFAULT 1
);


--
-- Name: t_ingestion_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_ingestion_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_ingestion_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_ingestion_log_id_seq OWNED BY public.t_ingestion_log.id;


--
-- Name: t_intent_node; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_intent_node (
    id bigint NOT NULL,
    tenant_id bigint,
    parent_id bigint,
    label character varying(64) NOT NULL,
    description character varying(256),
    level smallint DEFAULT 1 NOT NULL,
    is_system smallint DEFAULT 0 NOT NULL,
    kb_id bigint,
    sort_order integer DEFAULT 0 NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    enabled integer DEFAULT 1
);


--
-- Name: t_knowledge_base; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_knowledge_base (
    id bigint NOT NULL,
    name character varying(128) NOT NULL,
    description character varying(512),
    created_by character varying(64),
    updated_by character varying(64),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 1,
    team_id bigint DEFAULT 0,
    owner_id bigint DEFAULT 0,
    visibility character varying(16) DEFAULT 'PRIVATE'::character varying,
    enabled integer DEFAULT 1,
    doc_count integer DEFAULT 0,
    chunk_count integer DEFAULT 0
);


--
-- Name: t_knowledge_document; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_knowledge_document (
    id bigint NOT NULL,
    kb_id bigint NOT NULL,
    doc_name character varying(256) NOT NULL,
    file_url character varying(1024),
    file_type character varying(64),
    file_size bigint,
    source_type character varying(32) DEFAULT 'file'::character varying NOT NULL,
    status character varying(32) DEFAULT 'pending'::character varying NOT NULL,
    enabled smallint DEFAULT 1 NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    content_md5 character varying(32),
    tenant_id bigint,
    search_vector tsvector
);


--
-- Name: t_knowledge_document_file; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_knowledge_document_file (
    id bigint NOT NULL,
    storage_key character varying(512) NOT NULL,
    content bytea NOT NULL,
    content_type character varying(128),
    create_time timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: t_knowledge_document_file_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_knowledge_document_file_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_knowledge_document_file_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_knowledge_document_file_id_seq OWNED BY public.t_knowledge_document_file.id;


--
-- Name: t_llm_model_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_llm_model_config (
    id bigint NOT NULL,
    model_name character varying(100) NOT NULL,
    provider character varying(50) NOT NULL,
    base_url character varying(500) NOT NULL,
    api_key text NOT NULL,
    model_id character varying(100) NOT NULL,
    max_tokens integer DEFAULT 4096,
    temperature double precision DEFAULT 0.7,
    is_active smallint DEFAULT 1,
    status character varying(20) DEFAULT 'DRAFT'::character varying,
    create_user_id bigint,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    tenant_id bigint
);


--
-- Name: t_llm_model_config_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_llm_model_config_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_llm_model_config_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_llm_model_config_id_seq OWNED BY public.t_llm_model_config.id;


--
-- Name: t_log_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_log_token (
    id bigint NOT NULL,
    token character varying(128) NOT NULL,
    service_name character varying(64),
    log_level character varying(8),
    time_bucket timestamp without time zone NOT NULL,
    doc_count bigint DEFAULT 0,
    create_time timestamp without time zone DEFAULT now()
);


--
-- Name: t_message_feedback; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_message_feedback (
    id bigint NOT NULL,
    trace_id character varying(64),
    conversation_id character varying(64) NOT NULL,
    message_id character varying(64) NOT NULL,
    user_id bigint NOT NULL,
    feedback_type smallint NOT NULL,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: t_message_feedback_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_message_feedback_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_message_feedback_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_message_feedback_id_seq OWNED BY public.t_message_feedback.id;


--
-- Name: t_permission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_permission (
    id bigint NOT NULL,
    perm_code character varying(128),
    perm_name character varying(128),
    resource_type character varying(32),
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    deleted smallint DEFAULT 0
);


--
-- Name: t_rag_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_rag_config (
    id bigint NOT NULL,
    config_name character varying(100) NOT NULL,
    intent_confidence_threshold double precision DEFAULT 0.5,
    retrieve_global_top_k integer DEFAULT 6,
    retrieve_intent_top_k integer DEFAULT 6,
    retrieve_fulltext_top_k integer DEFAULT 6,
    retrieve_timeout_sec integer DEFAULT 10,
    rrf_k double precision DEFAULT 60.0,
    rerank_top_k integer DEFAULT 3,
    rerank_confidence_threshold double precision DEFAULT 0.5,
    rerank_doc_truncate integer DEFAULT 800,
    hyde_enabled smallint DEFAULT 0,
    hyde_experiment_mode smallint DEFAULT 0,
    hyde_experiment_ratio double precision DEFAULT 0.5,
    hyde_equiv_query_count integer DEFAULT 3,
    token_budget integer DEFAULT 3000,
    token_estimate_coefficient double precision DEFAULT 0.4,
    gray_enabled smallint DEFAULT 0,
    gray_ratio double precision DEFAULT 0.1,
    gray_mode character varying(20) DEFAULT 'PERCENT'::character varying,
    gray_user_ids text,
    is_active smallint DEFAULT 0,
    tenant_id bigint DEFAULT 1,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    create_user_id bigint,
    update_user_id bigint,
    status character varying(20) DEFAULT 'DRAFT'::character varying
);


--
-- Name: t_rag_config_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_rag_config_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_rag_config_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_rag_config_id_seq OWNED BY public.t_rag_config.id;


--
-- Name: t_rag_config_version; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_rag_config_version (
    id bigint NOT NULL,
    config_id bigint NOT NULL,
    version_no integer NOT NULL,
    snapshot_data text NOT NULL,
    change_log character varying(500),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    create_user_id bigint,
    tenant_id bigint DEFAULT 1
);


--
-- Name: t_rag_config_version_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_rag_config_version_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_rag_config_version_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_rag_config_version_id_seq OWNED BY public.t_rag_config_version.id;


--
-- Name: t_rag_experiment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_rag_experiment (
    id bigint NOT NULL,
    experiment_name character varying(100) NOT NULL,
    config_version_id bigint,
    test_question_ids text NOT NULL,
    intent_accuracy double precision,
    rewrite_accuracy double precision,
    hyde_relevance double precision,
    recall_at_3 double precision,
    recall_at_5 double precision,
    recall_at_10 double precision,
    mrr double precision,
    rerank_ndcg_at_3 double precision,
    rerank_ndcg_at_5 double precision,
    answer_faithfulness double precision,
    answer_completeness double precision,
    hallucination_rate double precision,
    detail_data text,
    status character varying(20) DEFAULT 'PENDING'::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    finish_time timestamp without time zone,
    create_user_id bigint,
    run_duration_ms bigint,
    tenant_id bigint DEFAULT 1
);


--
-- Name: t_rag_experiment_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_rag_experiment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_rag_experiment_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_rag_experiment_id_seq OWNED BY public.t_rag_experiment.id;


--
-- Name: t_rag_experiment_plan; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_rag_experiment_plan (
    id bigint NOT NULL,
    plan_name character varying(100) NOT NULL,
    model_config_id bigint,
    config_version_id bigint,
    prompt_version_id bigint,
    use_global_questions boolean DEFAULT true,
    use_custom_questions boolean DEFAULT false,
    status character varying(20) DEFAULT 'PENDING'::character varying,
    result_summary text,
    create_user_id bigint,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    finish_time timestamp without time zone,
    tenant_id bigint,
    knowledge_base_ids character varying(500) DEFAULT NULL::character varying
);


--
-- Name: t_rag_experiment_plan_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_rag_experiment_plan_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_rag_experiment_plan_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_rag_experiment_plan_id_seq OWNED BY public.t_rag_experiment_plan.id;


--
-- Name: t_rag_plan_custom_question; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_rag_plan_custom_question (
    id bigint NOT NULL,
    plan_id bigint NOT NULL,
    question_text text NOT NULL,
    expected_answer text,
    expected_doc_ids text,
    create_user_id bigint,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: t_rag_plan_custom_question_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_rag_plan_custom_question_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_rag_plan_custom_question_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_rag_plan_custom_question_id_seq OWNED BY public.t_rag_plan_custom_question.id;


--
-- Name: t_rag_plan_question_ref; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_rag_plan_question_ref (
    id bigint NOT NULL,
    plan_id bigint NOT NULL,
    question_id bigint NOT NULL
);


--
-- Name: t_rag_plan_question_ref_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_rag_plan_question_ref_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_rag_plan_question_ref_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_rag_plan_question_ref_id_seq OWNED BY public.t_rag_plan_question_ref.id;


--
-- Name: t_rag_prompt_template; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_rag_prompt_template (
    id bigint NOT NULL,
    prompt_type character varying(50) NOT NULL,
    template_name character varying(100) NOT NULL,
    template_content text NOT NULL,
    is_active smallint DEFAULT 1,
    tenant_id bigint DEFAULT 1,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    create_user_id bigint,
    update_user_id bigint,
    status character varying(20) DEFAULT 'DRAFT'::character varying
);


--
-- Name: t_rag_prompt_template_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_rag_prompt_template_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_rag_prompt_template_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_rag_prompt_template_id_seq OWNED BY public.t_rag_prompt_template.id;


--
-- Name: t_rag_prompt_version; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_rag_prompt_version (
    id bigint NOT NULL,
    prompt_id bigint NOT NULL,
    version_no integer NOT NULL,
    template_content text NOT NULL,
    change_log character varying(500),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    create_user_id bigint,
    tenant_id bigint DEFAULT 1
);


--
-- Name: t_rag_prompt_version_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_rag_prompt_version_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_rag_prompt_version_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_rag_prompt_version_id_seq OWNED BY public.t_rag_prompt_version.id;


--
-- Name: t_rag_test_document; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_rag_test_document (
    id bigint NOT NULL,
    doc_title character varying(200),
    doc_content text NOT NULL,
    doc_category character varying(50),
    tenant_id bigint DEFAULT 1,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    create_user_id bigint
);


--
-- Name: t_rag_test_document_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_rag_test_document_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_rag_test_document_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_rag_test_document_id_seq OWNED BY public.t_rag_test_document.id;


--
-- Name: t_rag_test_question; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_rag_test_question (
    id bigint NOT NULL,
    question_text text NOT NULL,
    expected_intent character varying(100),
    expected_rewritten text,
    expected_hyde_doc text,
    expected_doc_ids text,
    standard_answer text,
    category character varying(50),
    difficulty character varying(20) DEFAULT 'MEDIUM'::character varying,
    tenant_id bigint DEFAULT 1,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    create_user_id bigint
);


--
-- Name: t_rag_test_question_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_rag_test_question_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_rag_test_question_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_rag_test_question_id_seq OWNED BY public.t_rag_test_question.id;


--
-- Name: t_rag_trace_node; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_rag_trace_node (
    id bigint NOT NULL,
    trace_id character varying(64) NOT NULL,
    node_id character varying(64) NOT NULL,
    node_name character varying(128),
    node_type character varying(32),
    status character varying(16) DEFAULT 'RUNNING'::character varying NOT NULL,
    error_message text,
    start_time timestamp without time zone DEFAULT now() NOT NULL,
    end_time timestamp without time zone,
    duration_ms bigint,
    input_data text,
    output_data text,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0
);


--
-- Name: t_rag_trace_node_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_rag_trace_node_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_rag_trace_node_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_rag_trace_node_id_seq OWNED BY public.t_rag_trace_node.id;


--
-- Name: t_rag_trace_run; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_rag_trace_run (
    id bigint NOT NULL,
    trace_id character varying(64) NOT NULL,
    conversation_id character varying(64),
    original_prompt text,
    status character varying(16) DEFAULT 'RUNNING'::character varying NOT NULL,
    error_message text,
    start_time timestamp without time zone DEFAULT now() NOT NULL,
    end_time timestamp without time zone,
    duration_ms bigint,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    gray_tag character varying(20) DEFAULT 'BASELINE'::character varying
);


--
-- Name: t_rag_trace_run_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_rag_trace_run_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_rag_trace_run_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_rag_trace_run_id_seq OWNED BY public.t_rag_trace_run.id;


--
-- Name: t_redis_key_stats; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_redis_key_stats (
    id bigint NOT NULL,
    key_name character varying(256) NOT NULL,
    key_type character varying(16),
    key_size bigint DEFAULT 0,
    access_count bigint DEFAULT 0,
    last_access_time timestamp without time zone,
    ttl bigint DEFAULT '-1'::integer,
    expire_time timestamp without time zone,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


--
-- Name: t_redis_key_stats_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_redis_key_stats_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_redis_key_stats_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_redis_key_stats_id_seq OWNED BY public.t_redis_key_stats.id;


--
-- Name: t_redis_latency; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_redis_latency (
    id bigint NOT NULL,
    latency_ms bigint DEFAULT 0,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: t_redis_latency_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_redis_latency_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_redis_latency_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_redis_latency_id_seq OWNED BY public.t_redis_latency.id;


--
-- Name: t_redis_memory; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_redis_memory (
    id bigint NOT NULL,
    used_memory bigint DEFAULT 0,
    max_memory bigint DEFAULT 0,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: t_redis_memory_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_redis_memory_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_redis_memory_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_redis_memory_id_seq OWNED BY public.t_redis_memory.id;


--
-- Name: t_redis_qps; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_redis_qps (
    id bigint NOT NULL,
    qps bigint DEFAULT 0,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: t_redis_qps_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_redis_qps_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_redis_qps_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_redis_qps_id_seq OWNED BY public.t_redis_qps.id;


--
-- Name: t_resource_access; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_resource_access (
    id bigint NOT NULL,
    resource_type character varying(32),
    resource_id bigint,
    grantee_type character varying(16),
    grantee_id bigint,
    permission character varying(16),
    granted_by bigint,
    create_time timestamp without time zone DEFAULT now(),
    tenant_id bigint
);


--
-- Name: t_retrieval_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_retrieval_log (
    id bigint NOT NULL,
    conversation_id character varying(64),
    message_id character varying(64),
    user_id bigint,
    tenant_id bigint,
    knowledge_base_id character varying(64),
    query_text text,
    result_count integer DEFAULT 0,
    relevance_score numeric(3,2),
    latency_ms integer,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: t_retrieval_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_retrieval_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_retrieval_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_retrieval_log_id_seq OWNED BY public.t_retrieval_log.id;


--
-- Name: t_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_role (
    id bigint NOT NULL,
    role_code character varying(64),
    role_name character varying(128),
    scope_type character varying(16),
    data_scope character varying(16),
    tenant_id bigint,
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    deleted smallint DEFAULT 0
);


--
-- Name: t_role_permission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_role_permission (
    id bigint NOT NULL,
    role_id bigint,
    permission_id bigint,
    access_level character varying(16) DEFAULT 'READ'::character varying
);


--
-- Name: t_tablespace; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_tablespace (
    id bigint NOT NULL,
    table_name character varying(256),
    table_size bigint DEFAULT 0,
    index_size bigint DEFAULT 0,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: t_tablespace_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_tablespace_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_tablespace_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_tablespace_id_seq OWNED BY public.t_tablespace.id;


--
-- Name: t_team; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_team (
    id bigint NOT NULL,
    team_name character varying(128),
    tenant_id bigint,
    owner_id bigint,
    status smallint DEFAULT 1,
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    deleted smallint DEFAULT 0,
    parent_id bigint DEFAULT 0
);


--
-- Name: t_tenant; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_tenant (
    id bigint NOT NULL,
    tenant_name character varying(128),
    tenant_code character varying(64),
    status smallint DEFAULT 1,
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    deleted smallint DEFAULT 0
);


--
-- Name: t_token_usage; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_token_usage (
    id bigint NOT NULL,
    conversation_id character varying(64),
    message_id character varying(64),
    user_id bigint,
    tenant_id bigint,
    input_tokens integer DEFAULT 0 NOT NULL,
    output_tokens integer DEFAULT 0 NOT NULL,
    total_tokens integer DEFAULT 0 NOT NULL,
    model character varying(64),
    created_at timestamp without time zone DEFAULT now(),
    usage_type character varying(20) DEFAULT 'CONVERSATION'::character varying
);


--
-- Name: t_token_usage_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_token_usage_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_token_usage_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_token_usage_id_seq OWNED BY public.t_token_usage.id;


--
-- Name: t_trace_span; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_trace_span (
    id bigint NOT NULL,
    trace_id character varying(64) NOT NULL,
    span_id character varying(32) NOT NULL,
    parent_span_id character varying(32),
    operation_name character varying(256),
    service_name character varying(64),
    start_time timestamp without time zone NOT NULL,
    duration_ms double precision,
    status character varying(16) DEFAULT 'OK'::character varying,
    tags text,
    create_time timestamp without time zone DEFAULT now()
);


--
-- Name: t_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_user (
    id bigint NOT NULL,
    username character varying(64),
    password character varying(128),
    nickname character varying(64),
    email character varying(128),
    tenant_id bigint,
    status smallint DEFAULT 1,
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    deleted smallint DEFAULT 0
);


--
-- Name: t_user_memory_profile; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_user_memory_profile (
    id bigint NOT NULL,
    user_id bigint,
    memory_json text,
    create_time timestamp without time zone DEFAULT now(),
    update_time timestamp without time zone DEFAULT now()
);


--
-- Name: t_user_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_user_role (
    id bigint NOT NULL,
    user_id bigint,
    role_id bigint
);


--
-- Name: t_user_team; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_user_team (
    id bigint NOT NULL,
    user_id bigint,
    team_id bigint,
    role_in_team character varying(32)
);


--
-- Name: v_ingestion_daily_stats; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.v_ingestion_daily_stats AS
 SELECT date(created_at) AS stat_date,
    kb_id,
    count(*) AS total_count,
    count(
        CASE
            WHEN ((status)::text = 'success'::text) THEN 1
            ELSE NULL::integer
        END) AS success_count,
    count(
        CASE
            WHEN ((status)::text = 'failed'::text) THEN 1
            ELSE NULL::integer
        END) AS failed_count,
    round(avg(
        CASE
            WHEN (total_duration_ms IS NOT NULL) THEN total_duration_ms
            ELSE NULL::integer
        END), 0) AS avg_duration_ms,
    round((max(
        CASE
            WHEN (total_duration_ms IS NOT NULL) THEN total_duration_ms
            ELSE NULL::integer
        END))::numeric, 0) AS max_duration_ms,
    round((sum(
        CASE
            WHEN ((stage)::text = 'complete'::text) THEN chunks_success
            ELSE 0
        END))::numeric, 0) AS total_chunks,
    round(avg(
        CASE
            WHEN ((stage)::text = 'chunk'::text) THEN chunks_count
            ELSE NULL::integer
        END), 0) AS avg_chunks_per_doc
   FROM public.t_ingestion_log
  GROUP BY (date(created_at)), kb_id;


--
-- Name: vector_store; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.vector_store (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    content text,
    metadata json,
    embedding public.vector(1024)
);


--
-- Name: t_chat_memory id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_chat_memory ALTER COLUMN id SET DEFAULT nextval('public.t_chat_memory_id_seq'::regclass);


--
-- Name: t_data_replay_request id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_data_replay_request ALTER COLUMN id SET DEFAULT nextval('public.t_data_replay_request_id_seq'::regclass);


--
-- Name: t_db_access id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_db_access ALTER COLUMN id SET DEFAULT nextval('public.t_db_access_id_seq'::regclass);


--
-- Name: t_db_qps id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_db_qps ALTER COLUMN id SET DEFAULT nextval('public.t_db_qps_id_seq'::regclass);


--
-- Name: t_db_slowquery id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_db_slowquery ALTER COLUMN id SET DEFAULT nextval('public.t_db_slowquery_id_seq'::regclass);


--
-- Name: t_gray_release_plan id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_gray_release_plan ALTER COLUMN id SET DEFAULT nextval('public.t_gray_release_plan_id_seq'::regclass);


--
-- Name: t_gray_release_plan_model id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_gray_release_plan_model ALTER COLUMN id SET DEFAULT nextval('public.t_gray_release_plan_model_id_seq'::regclass);


--
-- Name: t_ingestion_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_ingestion_log ALTER COLUMN id SET DEFAULT nextval('public.t_ingestion_log_id_seq'::regclass);


--
-- Name: t_knowledge_document_file id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_knowledge_document_file ALTER COLUMN id SET DEFAULT nextval('public.t_knowledge_document_file_id_seq'::regclass);


--
-- Name: t_llm_model_config id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_llm_model_config ALTER COLUMN id SET DEFAULT nextval('public.t_llm_model_config_id_seq'::regclass);


--
-- Name: t_message_feedback id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_message_feedback ALTER COLUMN id SET DEFAULT nextval('public.t_message_feedback_id_seq'::regclass);


--
-- Name: t_rag_config id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_rag_config ALTER COLUMN id SET DEFAULT nextval('public.t_rag_config_id_seq'::regclass);


--
-- Name: t_rag_config_version id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_rag_config_version ALTER COLUMN id SET DEFAULT nextval('public.t_rag_config_version_id_seq'::regclass);


--
-- Name: t_rag_experiment id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_rag_experiment ALTER COLUMN id SET DEFAULT nextval('public.t_rag_experiment_id_seq'::regclass);


--
-- Name: t_rag_experiment_plan id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_rag_experiment_plan ALTER COLUMN id SET DEFAULT nextval('public.t_rag_experiment_plan_id_seq'::regclass);


--
-- Name: t_rag_plan_custom_question id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_rag_plan_custom_question ALTER COLUMN id SET DEFAULT nextval('public.t_rag_plan_custom_question_id_seq'::regclass);


--
-- Name: t_rag_plan_question_ref id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_rag_plan_question_ref ALTER COLUMN id SET DEFAULT nextval('public.t_rag_plan_question_ref_id_seq'::regclass);


--
-- Name: t_rag_prompt_template id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_rag_prompt_template ALTER COLUMN id SET DEFAULT nextval('public.t_rag_prompt_template_id_seq'::regclass);


--
-- Name: t_rag_prompt_version id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_rag_prompt_version ALTER COLUMN id SET DEFAULT nextval('public.t_rag_prompt_version_id_seq'::regclass);


--
-- Name: t_rag_test_document id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_rag_test_document ALTER COLUMN id SET DEFAULT nextval('public.t_rag_test_document_id_seq'::regclass);


--
-- Name: t_rag_test_question id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_rag_test_question ALTER COLUMN id SET DEFAULT nextval('public.t_rag_test_question_id_seq'::regclass);


--
-- Name: t_rag_trace_node id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_rag_trace_node ALTER COLUMN id SET DEFAULT nextval('public.t_rag_trace_node_id_seq'::regclass);


--
-- Name: t_rag_trace_run id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_rag_trace_run ALTER COLUMN id SET DEFAULT nextval('public.t_rag_trace_run_id_seq'::regclass);


--
-- Name: t_redis_key_stats id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_redis_key_stats ALTER COLUMN id SET DEFAULT nextval('public.t_redis_key_stats_id_seq'::regclass);


--
-- Name: t_redis_latency id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_redis_latency ALTER COLUMN id SET DEFAULT nextval('public.t_redis_latency_id_seq'::regclass);


--
-- Name: t_redis_memory id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_redis_memory ALTER COLUMN id SET DEFAULT nextval('public.t_redis_memory_id_seq'::regclass);


--
-- Name: t_redis_qps id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_redis_qps ALTER COLUMN id SET DEFAULT nextval('public.t_redis_qps_id_seq'::regclass);


--
-- Name: t_retrieval_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_retrieval_log ALTER COLUMN id SET DEFAULT nextval('public.t_retrieval_log_id_seq'::regclass);


--
-- Name: t_tablespace id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_tablespace ALTER COLUMN id SET DEFAULT nextval('public.t_tablespace_id_seq'::regclass);


--
-- Name: t_token_usage id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_token_usage ALTER COLUMN id SET DEFAULT nextval('public.t_token_usage_id_seq'::regclass);


--
-- Name: t_alert_record t_alert_record_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_alert_record
    ADD CONSTRAINT t_alert_record_pkey PRIMARY KEY (id);


--
-- Name: t_alert_rule t_alert_rule_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_alert_rule
    ADD CONSTRAINT t_alert_rule_pkey PRIMARY KEY (id);


--
-- Name: t_app_log t_app_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_app_log
    ADD CONSTRAINT t_app_log_pkey PRIMARY KEY (id);


--
-- Name: t_approval t_approval_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_approval
    ADD CONSTRAINT t_approval_pkey PRIMARY KEY (id);


--
-- Name: t_audit_log t_audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_audit_log
    ADD CONSTRAINT t_audit_log_pkey PRIMARY KEY (id);


--
-- Name: t_chat_memory t_chat_memory_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_chat_memory
    ADD CONSTRAINT t_chat_memory_pkey PRIMARY KEY (id);


--
-- Name: t_chat_message_compression t_chat_message_compression_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_chat_message_compression
    ADD CONSTRAINT t_chat_message_compression_pkey PRIMARY KEY (id);


--
-- Name: t_chat_message_raw t_chat_message_raw_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_chat_message_raw
    ADD CONSTRAINT t_chat_message_raw_pkey PRIMARY KEY (id);


--
-- Name: t_conversation t_conversation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_conversation
    ADD CONSTRAINT t_conversation_pkey PRIMARY KEY (id);


--
-- Name: t_data_replay_request t_data_replay_request_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_data_replay_request
    ADD CONSTRAINT t_data_replay_request_pkey PRIMARY KEY (id);


--
-- Name: t_db_access t_db_access_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_db_access
    ADD CONSTRAINT t_db_access_pkey PRIMARY KEY (id);


--
-- Name: t_db_qps t_db_qps_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_db_qps
    ADD CONSTRAINT t_db_qps_pkey PRIMARY KEY (id);


--
-- Name: t_db_slowquery t_db_slowquery_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_db_slowquery
    ADD CONSTRAINT t_db_slowquery_pkey PRIMARY KEY (id);


--
-- Name: t_event t_event_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_event
    ADD CONSTRAINT t_event_pkey PRIMARY KEY (id);


--
-- Name: t_gray_release_plan_model t_gray_release_plan_model_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_gray_release_plan_model
    ADD CONSTRAINT t_gray_release_plan_model_pkey PRIMARY KEY (id);


--
-- Name: t_gray_release_plan t_gray_release_plan_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_gray_release_plan
    ADD CONSTRAINT t_gray_release_plan_pkey PRIMARY KEY (id);


--
-- Name: t_heartbeat t_heartbeat_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_heartbeat
    ADD CONSTRAINT t_heartbeat_pkey PRIMARY KEY (id);


--
-- Name: t_ingestion_log t_ingestion_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_ingestion_log
    ADD CONSTRAINT t_ingestion_log_pkey PRIMARY KEY (id);


--
-- Name: t_intent_node t_intent_node_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_intent_node
    ADD CONSTRAINT t_intent_node_pkey PRIMARY KEY (id);


--
-- Name: t_knowledge_base t_knowledge_base_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_knowledge_base
    ADD CONSTRAINT t_knowledge_base_pkey PRIMARY KEY (id);


--
-- Name: t_knowledge_document_file t_knowledge_document_file_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_knowledge_document_file
    ADD CONSTRAINT t_knowledge_document_file_pkey PRIMARY KEY (id);


--
-- Name: t_knowledge_document_file t_knowledge_document_file_storage_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_knowledge_document_file
    ADD CONSTRAINT t_knowledge_document_file_storage_key_key UNIQUE (storage_key);


--
-- Name: t_knowledge_document t_knowledge_document_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_knowledge_document
    ADD CONSTRAINT t_knowledge_document_pkey PRIMARY KEY (id);


--
-- Name: t_llm_model_config t_llm_model_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_llm_model_config
    ADD CONSTRAINT t_llm_model_config_pkey PRIMARY KEY (id);


--
-- Name: t_log_token t_log_token_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_log_token
    ADD CONSTRAINT t_log_token_pkey PRIMARY KEY (id);


--
-- Name: t_message_feedback t_message_feedback_conversation_id_message_id_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_message_feedback
    ADD CONSTRAINT t_message_feedback_conversation_id_message_id_user_id_key UNIQUE (conversation_id, message_id, user_id);


--
-- Name: t_message_feedback t_message_feedback_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_message_feedback
    ADD CONSTRAINT t_message_feedback_pkey PRIMARY KEY (id);


--
-- Name: t_permission t_permission_perm_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_permission
    ADD CONSTRAINT t_permission_perm_code_key UNIQUE (perm_code);


--
-- Name: t_permission t_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_permission
    ADD CONSTRAINT t_permission_pkey PRIMARY KEY (id);


--
-- Name: t_rag_config t_rag_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_rag_config
    ADD CONSTRAINT t_rag_config_pkey PRIMARY KEY (id);


--
-- Name: t_rag_config_version t_rag_config_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_rag_config_version
    ADD CONSTRAINT t_rag_config_version_pkey PRIMARY KEY (id);


--
-- Name: t_rag_experiment t_rag_experiment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_rag_experiment
    ADD CONSTRAINT t_rag_experiment_pkey PRIMARY KEY (id);


--
-- Name: t_rag_experiment_plan t_rag_experiment_plan_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_rag_experiment_plan
    ADD CONSTRAINT t_rag_experiment_plan_pkey PRIMARY KEY (id);


--
-- Name: t_rag_plan_custom_question t_rag_plan_custom_question_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_rag_plan_custom_question
    ADD CONSTRAINT t_rag_plan_custom_question_pkey PRIMARY KEY (id);


--
-- Name: t_rag_plan_question_ref t_rag_plan_question_ref_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_rag_plan_question_ref
    ADD CONSTRAINT t_rag_plan_question_ref_pkey PRIMARY KEY (id);


--
-- Name: t_rag_prompt_template t_rag_prompt_template_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_rag_prompt_template
    ADD CONSTRAINT t_rag_prompt_template_pkey PRIMARY KEY (id);


--
-- Name: t_rag_prompt_template t_rag_prompt_template_prompt_type_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_rag_prompt_template
    ADD CONSTRAINT t_rag_prompt_template_prompt_type_key UNIQUE (prompt_type);


--
-- Name: t_rag_prompt_version t_rag_prompt_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_rag_prompt_version
    ADD CONSTRAINT t_rag_prompt_version_pkey PRIMARY KEY (id);


--
-- Name: t_rag_test_document t_rag_test_document_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_rag_test_document
    ADD CONSTRAINT t_rag_test_document_pkey PRIMARY KEY (id);


--
-- Name: t_rag_test_question t_rag_test_question_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_rag_test_question
    ADD CONSTRAINT t_rag_test_question_pkey PRIMARY KEY (id);


--
-- Name: t_rag_trace_node t_rag_trace_node_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_rag_trace_node
    ADD CONSTRAINT t_rag_trace_node_pkey PRIMARY KEY (id);


--
-- Name: t_rag_trace_run t_rag_trace_run_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_rag_trace_run
    ADD CONSTRAINT t_rag_trace_run_pkey PRIMARY KEY (id);


--
-- Name: t_rag_trace_run t_rag_trace_run_trace_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_rag_trace_run
    ADD CONSTRAINT t_rag_trace_run_trace_id_key UNIQUE (trace_id);


--
-- Name: t_redis_key_stats t_redis_key_stats_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_redis_key_stats
    ADD CONSTRAINT t_redis_key_stats_pkey PRIMARY KEY (id);


--
-- Name: t_redis_latency t_redis_latency_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_redis_latency
    ADD CONSTRAINT t_redis_latency_pkey PRIMARY KEY (id);


--
-- Name: t_redis_memory t_redis_memory_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_redis_memory
    ADD CONSTRAINT t_redis_memory_pkey PRIMARY KEY (id);


--
-- Name: t_redis_qps t_redis_qps_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_redis_qps
    ADD CONSTRAINT t_redis_qps_pkey PRIMARY KEY (id);


--
-- Name: t_resource_access t_resource_access_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_resource_access
    ADD CONSTRAINT t_resource_access_pkey PRIMARY KEY (id);


--
-- Name: t_retrieval_log t_retrieval_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_retrieval_log
    ADD CONSTRAINT t_retrieval_log_pkey PRIMARY KEY (id);


--
-- Name: t_role_permission t_role_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_role_permission
    ADD CONSTRAINT t_role_permission_pkey PRIMARY KEY (id);


--
-- Name: t_role_permission t_role_permission_role_id_permission_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_role_permission
    ADD CONSTRAINT t_role_permission_role_id_permission_id_key UNIQUE (role_id, permission_id);


--
-- Name: t_role t_role_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_role
    ADD CONSTRAINT t_role_pkey PRIMARY KEY (id);


--
-- Name: t_role t_role_role_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_role
    ADD CONSTRAINT t_role_role_code_key UNIQUE (role_code);


--
-- Name: t_tablespace t_tablespace_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_tablespace
    ADD CONSTRAINT t_tablespace_pkey PRIMARY KEY (id);


--
-- Name: t_team t_team_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_team
    ADD CONSTRAINT t_team_pkey PRIMARY KEY (id);


--
-- Name: t_tenant t_tenant_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_tenant
    ADD CONSTRAINT t_tenant_pkey PRIMARY KEY (id);


--
-- Name: t_tenant t_tenant_tenant_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_tenant
    ADD CONSTRAINT t_tenant_tenant_code_key UNIQUE (tenant_code);


--
-- Name: t_token_usage t_token_usage_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_token_usage
    ADD CONSTRAINT t_token_usage_pkey PRIMARY KEY (id);


--
-- Name: t_trace_span t_trace_span_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_trace_span
    ADD CONSTRAINT t_trace_span_pkey PRIMARY KEY (id);


--
-- Name: t_user_memory_profile t_user_memory_profile_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_user_memory_profile
    ADD CONSTRAINT t_user_memory_profile_pkey PRIMARY KEY (id);


--
-- Name: t_user_memory_profile t_user_memory_profile_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_user_memory_profile
    ADD CONSTRAINT t_user_memory_profile_user_id_key UNIQUE (user_id);


--
-- Name: t_user t_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_user
    ADD CONSTRAINT t_user_pkey PRIMARY KEY (id);


--
-- Name: t_user_role t_user_role_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_user_role
    ADD CONSTRAINT t_user_role_pkey PRIMARY KEY (id);


--
-- Name: t_user_role t_user_role_user_id_role_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_user_role
    ADD CONSTRAINT t_user_role_user_id_role_id_key UNIQUE (user_id, role_id);


--
-- Name: t_user_team t_user_team_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_user_team
    ADD CONSTRAINT t_user_team_pkey PRIMARY KEY (id);


--
-- Name: t_user_team t_user_team_user_id_team_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_user_team
    ADD CONSTRAINT t_user_team_user_id_team_id_key UNIQUE (user_id, team_id);


--
-- Name: t_user t_user_username_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_user
    ADD CONSTRAINT t_user_username_key UNIQUE (username);


--
-- Name: vector_store vector_store_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vector_store
    ADD CONSTRAINT vector_store_pkey PRIMARY KEY (id);


--
-- Name: idx_app_log_event_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_log_event_type ON public.t_app_log USING btree (event_type, log_ts DESC);


--
-- Name: idx_app_log_level; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_log_level ON public.t_app_log USING btree (log_level, log_ts DESC);


--
-- Name: idx_app_log_node_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_log_node_id ON public.t_app_log USING btree (node_id);


--
-- Name: idx_app_log_parent_trace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_log_parent_trace ON public.t_app_log USING btree (parent_trace_id);


--
-- Name: idx_app_log_service; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_log_service ON public.t_app_log USING btree (service_name, log_ts DESC);


--
-- Name: idx_app_log_trace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_log_trace ON public.t_app_log USING btree (trace_id);


--
-- Name: idx_app_log_ts; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_log_ts ON public.t_app_log USING btree (log_ts DESC);


--
-- Name: idx_app_log_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_log_type ON public.t_app_log USING btree (log_type, log_ts DESC);


--
-- Name: idx_approval_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_approval_tenant ON public.t_approval USING btree (tenant_id, status, apply_time DESC);


--
-- Name: idx_audit_log_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_tenant ON public.t_audit_log USING btree (tenant_id, create_time DESC);


--
-- Name: idx_chat_memory_conversation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_memory_conversation ON public.t_chat_memory USING btree (conversation_id, id);


--
-- Name: idx_compression_conversation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_compression_conversation ON public.t_chat_message_compression USING btree (conversation_id, create_time);


--
-- Name: idx_conversation_tenant_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_conversation_tenant_user ON public.t_conversation USING btree (tenant_id, user_id, created_at DESC);


--
-- Name: idx_data_replay_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_data_replay_status ON public.t_data_replay_request USING btree (status);


--
-- Name: idx_db_access_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_db_access_time ON public.t_db_access USING btree (created_at DESC);


--
-- Name: idx_db_qps_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_db_qps_time ON public.t_db_qps USING btree (created_at DESC);


--
-- Name: idx_db_slowquery_exec_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_db_slowquery_exec_time ON public.t_db_slowquery USING btree (execution_time DESC);


--
-- Name: idx_db_slowquery_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_db_slowquery_time ON public.t_db_slowquery USING btree (created_at DESC);


--
-- Name: idx_doc_file_storage_key; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_file_storage_key ON public.t_knowledge_document_file USING btree (storage_key);


--
-- Name: idx_doc_search; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_search ON public.t_knowledge_document USING gin (search_vector);


--
-- Name: idx_event_service; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_event_service ON public.t_event USING btree (service_name, event_time DESC);


--
-- Name: idx_event_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_event_time ON public.t_event USING btree (event_time DESC);


--
-- Name: idx_event_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_event_type ON public.t_event USING btree (event_type, event_time DESC);


--
-- Name: idx_feedback_conversation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_feedback_conversation ON public.t_message_feedback USING btree (conversation_id);


--
-- Name: idx_feedback_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_feedback_user ON public.t_message_feedback USING btree (user_id);


--
-- Name: idx_heartbeat_host; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_heartbeat_host ON public.t_heartbeat USING btree (host, heartbeat_time DESC);


--
-- Name: idx_heartbeat_service; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_heartbeat_service ON public.t_heartbeat USING btree (service_name, heartbeat_time DESC);


--
-- Name: idx_heartbeat_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_heartbeat_time ON public.t_heartbeat USING btree (heartbeat_time DESC);


--
-- Name: idx_ingestion_log_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ingestion_log_date ON public.t_ingestion_log USING btree (date(created_at));


--
-- Name: idx_ingestion_log_doc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ingestion_log_doc ON public.t_ingestion_log USING btree (doc_id);


--
-- Name: idx_ingestion_log_kb; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ingestion_log_kb ON public.t_ingestion_log USING btree (kb_id);


--
-- Name: idx_ingestion_log_stage; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ingestion_log_stage ON public.t_ingestion_log USING btree (stage, created_at DESC);


--
-- Name: idx_ingestion_log_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ingestion_log_status ON public.t_ingestion_log USING btree (status, created_at DESC);


--
-- Name: idx_intent_node_level; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_intent_node_level ON public.t_intent_node USING btree (level);


--
-- Name: idx_intent_node_parent_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_intent_node_parent_id ON public.t_intent_node USING btree (parent_id);


--
-- Name: idx_intent_node_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_intent_node_tenant ON public.t_intent_node USING btree (tenant_id, parent_id);


--
-- Name: idx_intent_node_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_intent_node_tenant_id ON public.t_intent_node USING btree (tenant_id);


--
-- Name: idx_kb_document_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_document_tenant ON public.t_knowledge_document USING btree (tenant_id, kb_id);


--
-- Name: idx_kb_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_tenant ON public.t_knowledge_base USING btree (tenant_id, deleted);


--
-- Name: idx_knowledge_document_kb_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_knowledge_document_kb_id ON public.t_knowledge_document USING btree (kb_id);


--
-- Name: idx_knowledge_document_search_vector; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_knowledge_document_search_vector ON public.t_knowledge_document USING gin (search_vector);


--
-- Name: idx_log_token_bucket; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_log_token_bucket ON public.t_log_token USING btree (time_bucket DESC);


--
-- Name: idx_log_token_service; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_log_token_service ON public.t_log_token USING btree (service_name, time_bucket DESC);


--
-- Name: idx_log_token_token; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_log_token_token ON public.t_log_token USING btree (token, time_bucket DESC);


--
-- Name: idx_plan_custom_q_plan; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_plan_custom_q_plan ON public.t_rag_plan_custom_question USING btree (plan_id);


--
-- Name: idx_plan_model_plan_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_plan_model_plan_id ON public.t_gray_release_plan_model USING btree (plan_id);


--
-- Name: idx_plan_question_ref_plan; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_plan_question_ref_plan ON public.t_rag_plan_question_ref USING btree (plan_id);


--
-- Name: idx_rag_config_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rag_config_active ON public.t_rag_config USING btree (is_active) WHERE (is_active = 1);


--
-- Name: idx_rag_config_ver_config; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rag_config_ver_config ON public.t_rag_config_version USING btree (config_id);


--
-- Name: idx_rag_prompt_ver_prompt; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rag_prompt_ver_prompt ON public.t_rag_prompt_version USING btree (prompt_id);


--
-- Name: idx_rag_trace_node_node_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rag_trace_node_node_id ON public.t_rag_trace_node USING btree (trace_id, node_id);


--
-- Name: idx_rag_trace_node_trace_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rag_trace_node_trace_id ON public.t_rag_trace_node USING btree (trace_id);


--
-- Name: idx_rag_trace_run_conversation_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rag_trace_run_conversation_id ON public.t_rag_trace_run USING btree (conversation_id);


--
-- Name: idx_rag_trace_run_create_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rag_trace_run_create_time ON public.t_rag_trace_run USING btree (create_time DESC);


--
-- Name: idx_rag_trace_run_trace_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rag_trace_run_trace_id ON public.t_rag_trace_run USING btree (trace_id);


--
-- Name: idx_raw_conversation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_raw_conversation ON public.t_chat_message_raw USING btree (conversation_id, create_time);


--
-- Name: idx_raw_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_raw_tenant ON public.t_chat_message_raw USING btree (tenant_id, conversation_id, create_time DESC);


--
-- Name: idx_redis_key_stats_access; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_redis_key_stats_access ON public.t_redis_key_stats USING btree (access_count DESC);


--
-- Name: idx_redis_key_stats_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_redis_key_stats_name ON public.t_redis_key_stats USING btree (key_name);


--
-- Name: idx_redis_key_stats_size; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_redis_key_stats_size ON public.t_redis_key_stats USING btree (key_size DESC);


--
-- Name: idx_redis_latency_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_redis_latency_time ON public.t_redis_latency USING btree (created_at DESC);


--
-- Name: idx_redis_memory_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_redis_memory_time ON public.t_redis_memory USING btree (created_at DESC);


--
-- Name: idx_redis_qps_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_redis_qps_time ON public.t_redis_qps USING btree (created_at DESC);


--
-- Name: idx_resource_access_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_resource_access_tenant ON public.t_resource_access USING btree (tenant_id, resource_type, grantee_type);


--
-- Name: idx_retrieval_log_conversation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_retrieval_log_conversation ON public.t_retrieval_log USING btree (conversation_id, created_at DESC);


--
-- Name: idx_retrieval_log_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_retrieval_log_date ON public.t_retrieval_log USING btree (date(created_at));


--
-- Name: idx_retrieval_log_kb; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_retrieval_log_kb ON public.t_retrieval_log USING btree (knowledge_base_id, created_at DESC);


--
-- Name: idx_retrieval_log_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_retrieval_log_user ON public.t_retrieval_log USING btree (user_id, created_at DESC);


--
-- Name: idx_tablespace_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tablespace_time ON public.t_tablespace USING btree (created_at DESC);


--
-- Name: idx_team_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_team_parent ON public.t_team USING btree (parent_id);


--
-- Name: idx_team_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_team_tenant ON public.t_team USING btree (tenant_id, deleted);


--
-- Name: idx_token_usage_conversation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_token_usage_conversation ON public.t_token_usage USING btree (conversation_id, created_at DESC);


--
-- Name: idx_token_usage_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_token_usage_date ON public.t_token_usage USING btree (date(created_at));


--
-- Name: idx_token_usage_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_token_usage_type ON public.t_token_usage USING btree (usage_type);


--
-- Name: idx_token_usage_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_token_usage_user ON public.t_token_usage USING btree (user_id, created_at DESC);


--
-- Name: idx_trace_span_service; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_trace_span_service ON public.t_trace_span USING btree (service_name, start_time DESC);


--
-- Name: idx_trace_span_start; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_trace_span_start ON public.t_trace_span USING btree (start_time DESC);


--
-- Name: idx_trace_span_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_trace_span_status ON public.t_trace_span USING btree (status);


--
-- Name: idx_trace_span_trace_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_trace_span_trace_id ON public.t_trace_span USING btree (trace_id);


--
-- Name: uk_kb_content_md5; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_kb_content_md5 ON public.t_knowledge_document USING btree (kb_id, content_md5) WHERE (deleted = 0);


--
-- Name: uk_knowledge_base_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_knowledge_base_name ON public.t_knowledge_base USING btree (name) WHERE (deleted = 0);


--
-- Name: uk_raw_msg_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_raw_msg_id ON public.t_chat_message_raw USING btree (msg_id);


--
-- Name: t_knowledge_document trg_knowledge_document_search_vector; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_knowledge_document_search_vector BEFORE INSERT OR UPDATE OF doc_name ON public.t_knowledge_document FOR EACH ROW EXECUTE FUNCTION public.update_knowledge_document_search_vector();


--
-- PostgreSQL database dump complete
--


