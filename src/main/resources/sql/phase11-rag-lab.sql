-- Phase 11: RAG 实验室 - 配置管理、提示词管理、测试评估、灰度配置
-- 创建时间: 2026-09-06

-- ============================================================
-- 1. t_rag_config — RAG 流水线参数配置
-- ============================================================
CREATE TABLE IF NOT EXISTS t_rag_config (
    id BIGSERIAL PRIMARY KEY,
    config_name VARCHAR(100) NOT NULL,

    -- 意图分类
    intent_confidence_threshold DOUBLE PRECISION DEFAULT 0.5,

    -- 检索
    retrieve_global_top_k INT DEFAULT 6,
    retrieve_intent_top_k INT DEFAULT 6,
    retrieve_fulltext_top_k INT DEFAULT 6,
    retrieve_timeout_sec INT DEFAULT 10,
    rrf_k DOUBLE PRECISION DEFAULT 60.0,

    -- Rerank
    rerank_top_k INT DEFAULT 3,
    rerank_confidence_threshold DOUBLE PRECISION DEFAULT 0.5,
    rerank_doc_truncate INT DEFAULT 800,

    -- HyDE
    hyde_enabled SMALLINT DEFAULT 0,
    hyde_experiment_mode SMALLINT DEFAULT 0,
    hyde_experiment_ratio DOUBLE PRECISION DEFAULT 0.5,
    hyde_equiv_query_count INT DEFAULT 3,

    -- Token
    token_budget INT DEFAULT 3000,
    token_estimate_coefficient DOUBLE PRECISION DEFAULT 0.4,

    -- 灰度
    gray_enabled SMALLINT DEFAULT 0,
    gray_ratio DOUBLE PRECISION DEFAULT 0.1,
    gray_mode VARCHAR(20) DEFAULT 'PERCENT',
    gray_user_ids TEXT,

    is_active SMALLINT DEFAULT 0,
    tenant_id BIGINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_user_id BIGINT,
    update_user_id BIGINT
);

CREATE INDEX IF NOT EXISTS idx_rag_config_active ON t_rag_config (is_active) WHERE is_active = 1;

-- 插入默认配置（is_active=1）
INSERT INTO t_rag_config (
    config_name,
    intent_confidence_threshold,
    retrieve_global_top_k, retrieve_intent_top_k, retrieve_fulltext_top_k,
    retrieve_timeout_sec, rrf_k,
    rerank_top_k, rerank_confidence_threshold, rerank_doc_truncate,
    hyde_enabled, hyde_experiment_mode, hyde_experiment_ratio, hyde_equiv_query_count,
    token_budget, token_estimate_coefficient,
    gray_enabled, gray_ratio, gray_mode,
    is_active, tenant_id
) VALUES (
    '默认配置',
    0.5,
    6, 6, 6,
    10, 60.0,
    3, 0.5, 800,
    0, 0, 0.5, 3,
    3000, 0.4,
    0, 0.1, 'PERCENT',
    1, 1
);

-- ============================================================
-- 2. t_rag_config_version — 配置版本历史
-- ============================================================
CREATE TABLE IF NOT EXISTS t_rag_config_version (
    id BIGSERIAL PRIMARY KEY,
    config_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    snapshot_data TEXT NOT NULL,
    change_log VARCHAR(500),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_user_id BIGINT,
    tenant_id BIGINT DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_rag_config_ver_config ON t_rag_config_version (config_id);

-- ============================================================
-- 3. t_rag_prompt_template — 提示词模板
-- ============================================================
CREATE TABLE IF NOT EXISTS t_rag_prompt_template (
    id BIGSERIAL PRIMARY KEY,
    prompt_type VARCHAR(50) NOT NULL UNIQUE,
    template_name VARCHAR(100) NOT NULL,
    template_content TEXT NOT NULL,
    is_active SMALLINT DEFAULT 1,
    tenant_id BIGINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_user_id BIGINT,
    update_user_id BIGINT
);

-- 迁移现有 .st 模板到数据库
INSERT INTO t_rag_prompt_template (prompt_type, template_name, template_content, is_active, tenant_id) VALUES
('QUERY_REWRITE', '查询改写提示词',
'你是一个查询改写专家。请将用户的问题改写为一个更适合向量检索的独立、完整的查询语句。
要求：
1. 去除代词指代，展开为完整句子
2. 保持原意不变
3. 如果问题有错别字，请纠正
4. 如果问题有歧义，请根据上下文消歧
5. 只输出改写后的查询，不要解释

原始问题：{{query}}

改写后的查询：', 1, 1),

('RERANK_SCORE', '文档评分提示词',
'请评估以下文档内容与用户问题的相关性，给出一个0到10的整数分数。
0分表示完全不相关，10分表示完全匹配。

用户问题：{{query}}

文档内容：{{content}}

请直接输出一个0到10之间的整数，不要输出其他内容。', 1, 1),

('INTENT_CLASSIFY', '意图分类提示词',
'根据以下意图节点树，判断用户问题属于哪个意图节点。
请只返回节点ID（数字），不要返回其他内容。
如果问题不属于任何节点，返回 -1。

意图节点树：
{{intentTree}}

用户问题：{{query}}

节点ID：', 1, 1),

('SYSTEM_CHAT', '系统闲聊提示词',
'你是{{name}}，一位知识渊博的{{domain}}领域专家。
请用简洁、专业的语言回答用户的问题。
如果问题超出你的知识范围，请坦诚说明。', 1, 1),

('HYDE_DOC', 'HyDE假设文档提示词',
'请根据以下问题，假设你是一个知识库，生成一段可能包含答案的文档内容（不超过200字）。
要求内容准确、简洁，涵盖问题涉及的关键概念。

问题：{{query}}

假设文档：', 1, 1),

('HYDE_EQUIV', 'HyDE等价查询提示词',
'请将以下问题改写为{{count}}个不同表述方式的等价查询，每行一个。
要求：
1. 保持原意不变
2. 使用不同的句式和词汇
3. 从不同角度提问

原始问题：{{query}}

等价查询：', 1, 1);

-- ============================================================
-- 4. t_rag_prompt_version — 提示词版本历史
-- ============================================================
CREATE TABLE IF NOT EXISTS t_rag_prompt_version (
    id BIGSERIAL PRIMARY KEY,
    prompt_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    template_content TEXT NOT NULL,
    change_log VARCHAR(500),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_user_id BIGINT,
    tenant_id BIGINT DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_rag_prompt_ver_prompt ON t_rag_prompt_version (prompt_id);

-- ============================================================
-- 5. t_rag_test_question — 测试题库
-- ============================================================
CREATE TABLE IF NOT EXISTS t_rag_test_question (
    id BIGSERIAL PRIMARY KEY,
    question_text TEXT NOT NULL,
    expected_intent VARCHAR(100),
    expected_rewritten TEXT,
    expected_hyde_doc TEXT,
    expected_doc_ids TEXT,
    standard_answer TEXT,
    category VARCHAR(50),
    difficulty VARCHAR(20) DEFAULT 'MEDIUM',
    tenant_id BIGINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_user_id BIGINT
);

-- ============================================================
-- 6. t_rag_test_document — 测试文档库
-- ============================================================
CREATE TABLE IF NOT EXISTS t_rag_test_document (
    id BIGSERIAL PRIMARY KEY,
    doc_title VARCHAR(200),
    doc_content TEXT NOT NULL,
    doc_category VARCHAR(50),
    tenant_id BIGINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_user_id BIGINT
);

-- ============================================================
-- 7. t_rag_experiment — 实验记录
-- ============================================================
CREATE TABLE IF NOT EXISTS t_rag_experiment (
    id BIGSERIAL PRIMARY KEY,
    experiment_name VARCHAR(100) NOT NULL,
    config_version_id BIGINT,
    test_question_ids TEXT NOT NULL,

    -- 指标汇总
    intent_accuracy DOUBLE PRECISION,
    rewrite_accuracy DOUBLE PRECISION,
    hyde_relevance DOUBLE PRECISION,
    recall_at_3 DOUBLE PRECISION,
    recall_at_5 DOUBLE PRECISION,
    recall_at_10 DOUBLE PRECISION,
    mrr DOUBLE PRECISION,
    rerank_ndcg_at_3 DOUBLE PRECISION,
    rerank_ndcg_at_5 DOUBLE PRECISION,
    answer_faithfulness DOUBLE PRECISION,
    answer_completeness DOUBLE PRECISION,
    hallucination_rate DOUBLE PRECISION,

    -- 明细数据
    detail_data TEXT,

    status VARCHAR(20) DEFAULT 'PENDING',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finish_time TIMESTAMP,
    create_user_id BIGINT,
    run_duration_ms BIGINT,
    tenant_id BIGINT DEFAULT 1
);

-- ============================================================
-- 8. t_rag_trace_run 新增 gray_tag 字段
-- ============================================================
ALTER TABLE t_rag_trace_run ADD COLUMN IF NOT EXISTS gray_tag VARCHAR(20) DEFAULT 'BASELINE';

-- ============================================================
-- 9. 测试文档数据（10篇）
-- ============================================================
INSERT INTO t_rag_test_document (doc_title, doc_content, doc_category) VALUES
('中华人民共和国宪法概述',
'宪法是国家的根本大法，规定了国家的根本制度和根本任务，具有最高的法律效力。中华人民共和国现行宪法是1982年12月4日第五届全国人民代表大会第五次会议通过的，此后经过了五次修正。宪法规定了国家的国体、政体、公民的基本权利和义务、国家机构等内容。宪法是治国安邦的总章程，是保持国家统一、民族团结、经济发展、社会进步和长治久安的法律基础。',
'法律'),

('民法典主要条款解读',
'《中华人民共和国民法典》是新中国第一部以法典命名的法律，于2021年1月1日起施行。民法典共7编1260条，包括总则、物权、合同、人格权、婚姻家庭、继承、侵权责任以及附则。民法典系统整合了新中国成立70多年来长期实践形成的民事法律规范，是一部固根本、稳预期、利长远的基础性法律。其中人格权编是中国民法典的重大创新，强化了对人格权的全面保护。',
'法律'),

('高中生物：细胞分裂详解',
'细胞分裂是生物体生长、发育、繁殖和遗传的基础。细胞分裂主要有两种方式：有丝分裂和减数分裂。有丝分裂是指一种真核细胞分裂产生体细胞的过程，其特点是有纺锤体丝的出现和染色体复制后平均分配到两个子细胞中。有丝分裂分为间期、前期、中期、后期和末期五个阶段。减数分裂是生殖细胞形成过程中特有的分裂方式，染色体复制一次，细胞连续分裂两次，最终形成四个染色体数目减半的子细胞。',
'教育'),

('光合作用原理与应用',
'光合作用是绿色植物利用光能将二氧化碳和水转化为有机物（主要是葡萄糖）并释放氧气的过程。光合作用的化学方程式为：6CO2+6H2O→C6H12O6+6O2。光合作用分为光反应和暗反应两个阶段。光反应在叶绿体的类囊体薄膜上进行，利用光能将水分解产生氧气和[H]，同时合成ATP。暗反应在叶绿体基质中进行，利用光反应产生的[H]和ATP将CO2固定并还原为有机物。光合作用是地球上最重要的化学反应之一，为几乎所有生物提供了食物和氧气来源。',
'教育'),

('中国近代史：鸦片战争',
'鸦片战争（1840-1842年）是中国近代史的开端。英国为了扭转对华贸易逆差，向中国大量走私鸦片，严重危害了中国社会。1839年林则徐虎门销烟成为战争的导火索。1840年6月，英国舰队侵入中国沿海，战争爆发。由于清政府腐败无力和军事技术落后，中国战败。1842年签订《南京条约》，这是中国近代史上第一个不平等条约，主要内容包括割让香港岛、赔款2100万银元、开放五口通商、协定关税等。鸦片战争使中国开始沦为半殖民地半封建社会。',
'历史'),

('人工智能发展简史',
'人工智能（AI）是研究开发用于模拟、延伸和扩展人的智能的理论、方法、技术及应用系统的一门技术科学。1956年达特茅斯会议标志着人工智能学科的诞生。20世纪60-70年代是AI的第一个黄金期，专家系统广泛使用。80年代AI进入低谷期。2006年Hinton提出深度学习概念，2012年AlexNet在ImageNet竞赛中获胜，标志着深度学习时代的到来。2016年AlphaGo击败李世石是AI发展的重要里程碑。2022年ChatGPT发布标志着大语言模型进入主流应用。当前AI正在深刻改变人类社会的方方面面。',
'科技'),

('机器学习基础算法概述',
'机器学习是人工智能的核心分支，使计算机能够从数据中学习并改进性能，而无需显式编程。机器学习主要分为三类：监督学习、无监督学习和强化学习。监督学习使用标注数据进行训练，常见算法包括线性回归、逻辑回归、决策树、支持向量机、随机森林和神经网络等。无监督学习处理未标注数据，主要用于聚类分析和降维，代表算法有K-Means、DBSCAN和PCA等。强化学习通过智能体与环境交互来获得最优策略，在游戏AI和机器人控制中应用广泛。',
'科技'),

('常见传染病预防措施',
'传染病是由病原体引起的能在人与人、动物与动物或人与动物之间相互传播的疾病。常见传染病包括流感、肺结核、肝炎、艾滋病等。预防传染病的关键措施包括：一是控制传染源，早发现、早隔离、早治疗；二是切断传播途径，注意个人卫生、勤洗手、保持环境通风、食物煮熟煮透；三是保护易感人群，接种疫苗是最有效的保护手段。此外，加强体育锻炼、合理膳食、充足睡眠等也有助于提高身体免疫力，减少感染风险。',
'医学'),

('健康饮食与营养搭配',
'健康饮食是维持身体健康的基础。中国居民膳食指南建议：食物多样，谷类为主；多吃蔬果、奶类、大豆；适量吃鱼、禽、蛋、瘦肉；少盐少油，控糖限酒。人体所需的六大营养素包括碳水化合物、蛋白质、脂肪、维生素、矿物质和水。碳水化合物是主要的能量来源，蛋白质是构成身体组织的基本物质，脂肪提供能量并帮助吸收脂溶性维生素。合理的膳食结构应保证三餐比例约为3:4:3，每天摄入12种以上食物，每周25种以上。',
'医学'),

('太阳系行星特征介绍',
'太阳系由太阳和围绕它运转的八大行星组成。按距太阳由近及远依次为：水星、金星、地球、火星、木星、土星、天王星、海王星。前四颗为类地行星，体积较小，主要由岩石和金属构成；后四颗为类木行星（巨行星），体积巨大，主要由气体和冰组成。水星是最小且最靠近太阳的行星；金星是温度最高的行星，表面温度约465°C；地球是目前已知唯一存在生命的星球；火星被称为红色星球，是人类探索的重点目标；木星是太阳系最大的行星；土星以其壮丽的光环著称。',
'科技')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 10. 测试问题数据（30道）
-- ============================================================

-- 直接查询类（6道）
INSERT INTO t_rag_test_question (question_text, expected_intent, expected_rewritten, expected_doc_ids, standard_answer, category, difficulty) VALUES
('宪法是什么？', '法律-宪法', '中华人民共和国宪法的定义和地位', '["1"]', '宪法是国家的根本大法，规定了国家的根本制度和根本任务，具有最高的法律效力。', 'direct', 'EASY'),

('民法典包括哪些编？', '法律-民法', '中华人民共和国民法典的组成部分', '["2"]', '民法典共7编1260条，包括总则、物权、合同、人格权、婚姻家庭、继承、侵权责任以及附则。', 'direct', 'EASY'),

('细胞分裂有哪些方式？', '教育-生物', '细胞分裂的主要方式', '["3"]', '细胞分裂主要有两种方式：有丝分裂和减数分裂。', 'direct', 'EASY'),

('光合作用的方程式是什么？', '教育-生物', '光合作用的化学反应方程式', '["4"]', '6CO2+6H2O→C6H12O6+6O2', 'direct', 'EASY'),

('鸦片战争是哪一年发生的？', '历史-近代史', '鸦片战争发生的时间', '["5"]', '鸦片战争发生在1840年至1842年。', 'direct', 'EASY'),

('人工智能是哪一年诞生的？', '科技-AI', '人工智能学科的诞生年份', '["6"]', '1956年达特茅斯会议标志着人工智能学科的诞生。', 'direct', 'EASY'),

-- 错别字类（6道）
('显法是什么？', '法律-宪法', '中华人民共和国宪法的定义', '["1"]', '宪法是国家的根本大法，规定了国家的根本制度和根本任务。', 'typo', 'MEDIUM'),

('名法典主要讲了什么？', '法律-民法', '民法典的主要内容', '["2"]', '民法典系统整合了民事法律规范，包括总则、物权、合同、人格权等内容。', 'typo', 'MEDIUM'),

('细胞分烈是什么意思？', '教育-生物', '细胞分裂的含义', '["3"]', '细胞分裂是生物体生长、发育、繁殖和遗传的基础。', 'typo', 'MEDIUM'),

('光和作用需要什么条件？', '教育-生物', '光合作用的条件', '["4"]', '光合作用需要光能、二氧化碳和水，在叶绿体中进行。', 'typo', 'MEDIUM'),

('片战争的原因是什么？', '历史-近代史', '鸦片战争爆发的原因', '["5"]', '英国为了扭转对华贸易逆差向中国走私鸦片，林则徐虎门销烟成为战争导火索。', 'typo', 'MEDIUM'),

('人共智能和机器学习有什么关系？', '科技-AI', '人工智能与机器学习的关系', '["6","7"]', '机器学习是人工智能的核心分支，使计算机能够从数据中学习并改进性能。', 'typo', 'MEDIUM'),

-- 歧义消解类（6道）
('它有什么效力？', '法律-宪法', '宪法的法律效力', '["1"]', '宪法具有最高的法律效力，是治国安邦的总章程。', 'ambiguous', 'HARD'),

('什么时候开始的？', '历史-近代史', '中国近代史的开端事件', '["5"]', '鸦片战争（1840年）是中国近代史的开端。', 'ambiguous', 'HARD'),

('有几个阶段？', '教育-生物', '光合作用的阶段划分', '["4"]', '光合作用分为光反应和暗反应两个阶段。', 'ambiguous', 'HARD'),

('它由什么组成？', '科技-AI', '太阳系的组成', '["10"]', '太阳系由太阳和围绕它运转的八大行星组成。', 'ambiguous', 'HARD'),

('如何预防？', '医学-传染病', '传染病的预防措施', '["8"]', '预防传染病关键措施：控制传染源、切断传播途径、保护易感人群。', 'ambiguous', 'HARD'),

('一天吃多少合适？', '医学-营养', '健康饮食的建议摄入量', '["9"]', '合理的膳食结构应保证三餐比例约为3:4:3，每天摄入12种以上食物。', 'ambiguous', 'HARD'),

-- 跨文档比较类（6道）
('比较宪法和民法典的区别', '法律-综合', '宪法与民法典的区别对比', '["1","2"]', '宪法是根本大法规定国家制度，民法典是民事领域的基础性法律规范私人关系。', 'cross', 'HARD'),

('有丝分裂和减数分裂有什么不同？', '教育-生物', '有丝分裂与减数分裂的区别', '["3"]', '有丝分裂产生体细胞染色体数不变，减数分裂产生生殖细胞染色体数减半。', 'cross', 'MEDIUM'),

('监督学习和无监督学习有什么区别？', '科技-AI', '监督学习与无监督学习的对比', '["7"]', '监督学习使用标注数据训练，无监督学习处理未标注数据。', 'cross', 'MEDIUM'),

('光反应和暗反应分别在哪个部位进行？', '教育-生物', '光反应和暗反应的场所', '["4"]', '光反应在叶绿体的类囊体薄膜上进行，暗反应在叶绿体基质中进行。', 'cross', 'MEDIUM'),

('类地行星和类木行星有什么区别？', '科技-天文', '类地行星与类木行星的对比', '["10"]', '类地行星体积小由岩石金属构成，类木行星体积大由气体和冰组成。', 'cross', 'MEDIUM'),

('传染病的三大预防措施分别是什么？', '医学-传染病', '传染病预防的三个关键措施', '["8"]', '三大措施：控制传染源、切断传播途径、保护易感人群。', 'cross', 'EASY'),

-- 无关问题类（6道）
('今天天气怎么样？', '闲聊-通用', NULL, '[]', '抱歉，我无法获取实时天气信息。', 'irrelevant', 'EASY'),

('帮我写一首诗', '闲聊-通用', NULL, '[]', '抱歉，我主要专注于知识问答。', 'irrelevant', 'EASY'),

('1+1等于多少？', '闲聊-通用', NULL, '[]', '1+1等于2。', 'irrelevant', 'EASY'),

('你有什么功能？', '闲聊-通用', NULL, '[]', '我是一个AI助手，可以进行知识问答。', 'irrelevant', 'EASY'),

('你是谁开发的？', '闲聊-通用', NULL, '[]', '我是一个人工智能助手。', 'irrelevant', 'EASY'),

('给我讲个笑话', '闲聊-通用', NULL, '[]', '抱歉，我更擅长回答知识类问题。', 'irrelevant', 'EASY')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 11. 页面权限注册（需在 auth-service 数据库执行）
-- ============================================================
-- 将以下 SQL 在 auth-service 数据库中执行：
-- INSERT INTO t_permission (id, perm_code, perm_name, resource_type) VALUES
--     (112, 'rag:lab', 'RAG实验室', 'PAGE')
-- ON CONFLICT DO NOTHING;
--
-- 为 SUPER_ADMIN 角色赋予 rag:lab 的 ADMIN 权限：
-- INSERT INTO t_role_permission (id, role_id, permission_id, access_level) VALUES
--     (1007, 1, 112, 'ADMIN')
-- ON CONFLICT DO NOTHING;
