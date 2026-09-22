-- FlashSale PostgreSQL 16 initial schema.
-- Monetary values are stored as integer minor units (for example, cents).

CREATE TYPE user_role AS ENUM ('CUSTOMER', 'OPERATOR', 'ADMIN');
-- user_role: CUSTOMER=普通消费者；OPERATOR=运营人员，管理商品/活动/优惠券；ADMIN=后台管理员，管理账户与角色。
COMMENT ON TYPE user_role IS '平台账户角色枚举；消费者、运营人员与后台管理员相互隔离。';
CREATE TYPE product_status AS ENUM ('DRAFT', 'ON_SALE', 'OFF_SALE');
-- product_status: DRAFT=草稿/尚未上架；ON_SALE=上架可直接购买；OFF_SALE=已下架。
COMMENT ON TYPE product_status IS '商品销售状态枚举。';
CREATE TYPE activity_status AS ENUM ('NOT_STARTED', 'ACTIVE', 'PAUSED', 'CANCELLED', 'ENDED');
-- activity_status: NOT_STARTED=未开始；ACTIVE=进行中；PAUSED=已暂停；CANCELLED=已取消；ENDED=已结束。
COMMENT ON TYPE activity_status IS '限时营销活动生命周期状态枚举。';
CREATE TYPE coupon_template_status AS ENUM ('DRAFT', 'ACTIVE', 'PAUSED', 'ENDED');
-- coupon_template_status: DRAFT=草稿；ACTIVE=允许领取；PAUSED=暂停领取；ENDED=停止领取。
COMMENT ON TYPE coupon_template_status IS '优惠券模板领取状态枚举。';
CREATE TYPE user_coupon_status AS ENUM ('AVAILABLE', 'RESERVED', 'CONSUMED', 'EXPIRED');
-- user_coupon_status: AVAILABLE=可使用；RESERVED=已被待支付订单锁定；CONSUMED=已核销；EXPIRED=已过期。
COMMENT ON TYPE user_coupon_status IS '用户持有优惠券的当前状态枚举。';
CREATE TYPE order_kind AS ENUM ('DIRECT', 'ACTIVITY');
-- order_kind: DIRECT=普通商品订单；ACTIVITY=限时活动订单。
COMMENT ON TYPE order_kind IS '订单来源类型：普通商品或限时活动。';
CREATE TYPE order_status AS ENUM ('PENDING_PAYMENT', 'PAID', 'COMPLETED', 'CANCELLED');
-- order_status: PENDING_PAYMENT=待支付；PAID=已支付；COMPLETED=已完成；CANCELLED=已取消。
COMMENT ON TYPE order_status IS '订单生命周期状态枚举。';
CREATE TYPE reservation_status AS ENUM ('RESERVED', 'CONFIRMED', 'RELEASED');
-- reservation_status: RESERVED=已保留；CONFIRMED=已确认使用/扣减；RELEASED=已释放。
COMMENT ON TYPE reservation_status IS '库存或优惠券保留记录状态枚举。';
CREATE TYPE inventory_source AS ENUM ('PRODUCT', 'ACTIVITY');
-- inventory_source: PRODUCT=商品库存；ACTIVITY=活动独立库存。
COMMENT ON TYPE inventory_source IS '库存保留所对应的库存池类型。';
CREATE TYPE inventory_movement_reason AS ENUM ('RESERVE', 'RELEASE', 'INITIALIZE', 'ADJUST');
-- inventory_movement_reason: RESERVE=订单保留；RELEASE=订单取消/超时释放；INITIALIZE=初始化库存；ADJUST=人工或对账调整。
COMMENT ON TYPE inventory_movement_reason IS '库存流水产生原因。';
CREATE TYPE payment_status AS ENUM ('PENDING', 'SUCCEEDED', 'FAILED');
-- payment_status: PENDING=待处理；SUCCEEDED=支付成功；FAILED=支付失败。
COMMENT ON TYPE payment_status IS '模拟支付记录状态枚举。';
CREATE TYPE fulfillment_status AS ENUM ('COMPLETED');
-- fulfillment_status: COMPLETED=模拟履约完成。
COMMENT ON TYPE fulfillment_status IS '模拟履约状态枚举。';
CREATE TYPE idempotency_status AS ENUM ('PROCESSING', 'SUCCEEDED', 'REJECTED');
-- idempotency_status: PROCESSING=处理中；SUCCEEDED=已成功并保存结果；REJECTED=已拒绝并保存结果。
COMMENT ON TYPE idempotency_status IS '下单幂等请求处理状态枚举。';
CREATE TYPE outbox_status AS ENUM ('PENDING', 'SENT', 'FAILED');
-- outbox_status: PENDING=待投递；SENT=已成功投递；FAILED=达到重试条件后暂时失败，等待补偿或人工处理。
COMMENT ON TYPE outbox_status IS '本地消息表的投递状态枚举。';
CREATE TYPE message_consumer_status AS ENUM ('PROCESSING', 'SUCCEEDED', 'FAILED');
-- message_consumer_status: PROCESSING=处理中；SUCCEEDED=已成功处理；FAILED=本次处理失败，可按策略重试。
COMMENT ON TYPE message_consumer_status IS '消息消费者幂等记录状态枚举。';
CREATE TYPE activity_inventory_event_kind AS ENUM ('RESERVE', 'RELEASE');
-- activity_inventory_event_kind: RESERVE=活动库存预扣；RELEASE=订单取消或超时后的活动库存回补。
COMMENT ON TYPE activity_inventory_event_kind IS '活动库存事件类型枚举；所有改变实时可售库存的事件都必须入账。';
CREATE TYPE activity_recovery_status AS ENUM ('PENDING', 'RUNNING', 'FAILED', 'SUCCEEDED');
-- activity_recovery_status: PENDING=等待恢复；RUNNING=正在追平/对账/预热；FAILED=恢复失败且保持暂停；SUCCEEDED=恢复完成并已激活活动。
COMMENT ON TYPE activity_recovery_status IS '异步活动恢复任务状态枚举。';

CREATE TABLE app_user (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role user_role NOT NULL DEFAULT 'CUSTOMER',
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (username)
);
COMMENT ON TABLE app_user IS '平台账户；CUSTOMER 为消费者，OPERATOR 为运营人员，ADMIN 为后台管理员。';
COMMENT ON COLUMN app_user.id IS '用户主键。';
COMMENT ON COLUMN app_user.username IS '登录用户名，平台内唯一。';
COMMENT ON COLUMN app_user.password_hash IS '密码哈希值，不保存明文密码。';
COMMENT ON COLUMN app_user.role IS '账户角色：CUSTOMER 只能消费，OPERATOR 只能运营业务，ADMIN 只能管理账户与角色。';
COMMENT ON COLUMN app_user.status IS '账户状态：ACTIVE 可用，DISABLED 已禁用。';
COMMENT ON COLUMN app_user.created_at IS '账户创建时间。';
COMMENT ON COLUMN app_user.updated_at IS '账户最后修改时间。';

CREATE TABLE product (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    list_price_minor BIGINT NOT NULL CHECK (list_price_minor >= 0),
    available_stock INTEGER NOT NULL DEFAULT 0 CHECK (available_stock >= 0),
    status product_status NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_by BIGINT NOT NULL REFERENCES app_user(id),
    updated_by BIGINT NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE product IS '商品目录及普通直接购买库存。活动订单不扣减本表库存。';
COMMENT ON COLUMN product.id IS '商品主键。';
COMMENT ON COLUMN product.sku IS '商品库存单位编码，商品内唯一。';
COMMENT ON COLUMN product.name IS '商品展示名称。';
COMMENT ON COLUMN product.description IS '商品详细描述。';
COMMENT ON COLUMN product.list_price_minor IS '商品标准价，使用最小货币单位整数保存。';
COMMENT ON COLUMN product.available_stock IS '普通直接订单可保留的剩余库存；活动订单不使用此库存池。';
COMMENT ON COLUMN product.status IS '商品状态；只有 ON_SALE 商品允许普通直接购买。';
COMMENT ON COLUMN product.version IS '乐观锁版本号，每次库存或配置更新递增。';
COMMENT ON COLUMN product.created_by IS '创建该商品的运营人员。';
COMMENT ON COLUMN product.updated_by IS '最后修改该商品的运营人员。';
COMMENT ON COLUMN product.created_at IS '商品创建时间。';
COMMENT ON COLUMN product.updated_at IS '商品最后修改时间。';

CREATE TABLE marketing_activity (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    product_id BIGINT NOT NULL REFERENCES product(id),
    sale_price_minor BIGINT NOT NULL CHECK (sale_price_minor >= 0),
    initial_stock INTEGER NOT NULL CHECK (initial_stock > 0),
    available_stock INTEGER NOT NULL CHECK (available_stock BETWEEN 0 AND initial_stock),
    purchase_limit_per_user INTEGER NOT NULL CHECK (purchase_limit_per_user > 0),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    status activity_status NOT NULL DEFAULT 'NOT_STARTED',
    pause_barrier_sequence BIGINT CHECK (pause_barrier_sequence IS NULL OR pause_barrier_sequence >= 0),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_by BIGINT NOT NULL REFERENCES app_user(id),
    updated_by BIGINT NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (starts_at < ends_at)
);
COMMENT ON TABLE marketing_activity IS '限时营销活动；每个活动只绑定一个商品，并维护独立活动库存。';
COMMENT ON COLUMN marketing_activity.id IS '活动主键。';
COMMENT ON COLUMN marketing_activity.name IS '活动展示名称。';
COMMENT ON COLUMN marketing_activity.product_id IS '活动绑定的唯一商品。';
COMMENT ON COLUMN marketing_activity.sale_price_minor IS '活动销售价，使用最小货币单位整数保存。';
COMMENT ON COLUMN marketing_activity.initial_stock IS '活动创建时配置的独立可售库存上限。';
COMMENT ON COLUMN marketing_activity.available_stock IS '活动库存的 PostgreSQL 持久化投影；活动进行中可因 Outbox/MQ 待处理而暂时滞后于 Redis 实时可售库存，不能直接用于恢复 Redis。';
COMMENT ON COLUMN marketing_activity.purchase_limit_per_user IS '单个用户在该活动中的累计购买数量上限，包含待支付保留量。';
COMMENT ON COLUMN marketing_activity.starts_at IS '活动开始时间；以数据库服务器时间判断是否开始。';
COMMENT ON COLUMN marketing_activity.ends_at IS '活动结束时间；结束时刻不再接受新的活动订单。';
COMMENT ON COLUMN marketing_activity.status IS '活动生命周期状态。';
COMMENT ON COLUMN marketing_activity.pause_barrier_sequence IS '关闭 Redis 新预扣门闸并清算在途预扣后，在数据库锁定活动事件序号分配器截取的最后已提交事件序号；恢复前必须追平。';
COMMENT ON COLUMN marketing_activity.version IS '活动配置/库存的乐观锁版本号。';
COMMENT ON COLUMN marketing_activity.created_by IS '创建该活动的运营人员。';
COMMENT ON COLUMN marketing_activity.updated_by IS '最后修改该活动的运营人员。';
COMMENT ON COLUMN marketing_activity.created_at IS '活动创建时间。';
COMMENT ON COLUMN marketing_activity.updated_at IS '活动最后修改时间。';
CREATE INDEX marketing_activity_browse_idx ON marketing_activity (status, starts_at, ends_at);
CREATE INDEX marketing_activity_product_idx ON marketing_activity (product_id);

CREATE TABLE coupon_template (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    threshold_minor BIGINT NOT NULL CHECK (threshold_minor >= 0),
    discount_minor BIGINT NOT NULL CHECK (discount_minor > 0),
    issue_limit INTEGER NOT NULL CHECK (issue_limit > 0),
    issued_count INTEGER NOT NULL DEFAULT 0 CHECK (issued_count BETWEEN 0 AND issue_limit),
    claim_limit_per_user INTEGER NOT NULL CHECK (claim_limit_per_user > 0),
    claim_starts_at TIMESTAMPTZ NOT NULL,
    claim_ends_at TIMESTAMPTZ NOT NULL,
    use_starts_at TIMESTAMPTZ NOT NULL,
    use_ends_at TIMESTAMPTZ NOT NULL,
    status coupon_template_status NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_by BIGINT NOT NULL REFERENCES app_user(id),
    updated_by BIGINT NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (claim_starts_at < claim_ends_at),
    CHECK (use_starts_at < use_ends_at),
    CHECK (discount_minor <= threshold_minor OR threshold_minor = 0)
);
COMMENT ON TABLE coupon_template IS '满减优惠券的发行规则与领取/使用时间窗口。';
COMMENT ON COLUMN coupon_template.id IS '优惠券模板主键。';
COMMENT ON COLUMN coupon_template.name IS '优惠券展示名称。';
COMMENT ON COLUMN coupon_template.threshold_minor IS '订单使用门槛，使用最小货币单位整数保存。';
COMMENT ON COLUMN coupon_template.discount_minor IS '满足门槛后固定减免金额，使用最小货币单位整数保存。';
COMMENT ON COLUMN coupon_template.issue_limit IS '该模板最多实际发放的券实例数量。';
COMMENT ON COLUMN coupon_template.issued_count IS '当前已发放的券实例数量，不能超过 issue_limit。';
COMMENT ON COLUMN coupon_template.claim_limit_per_user IS '单个用户最多领取该模板的数量。';
COMMENT ON COLUMN coupon_template.claim_starts_at IS '允许用户领取优惠券的开始时间。';
COMMENT ON COLUMN coupon_template.claim_ends_at IS '允许用户领取优惠券的结束时间。';
COMMENT ON COLUMN coupon_template.use_starts_at IS '优惠券允许被订单使用的开始时间。';
COMMENT ON COLUMN coupon_template.use_ends_at IS '优惠券允许被订单使用的结束时间。';
COMMENT ON COLUMN coupon_template.status IS '优惠券模板领取控制状态。';
COMMENT ON COLUMN coupon_template.version IS '优惠券模板配置的乐观锁版本号。';
COMMENT ON COLUMN coupon_template.created_by IS '创建该优惠券模板的运营人员。';
COMMENT ON COLUMN coupon_template.updated_by IS '最后修改该优惠券模板的运营人员。';
COMMENT ON COLUMN coupon_template.created_at IS '优惠券模板创建时间。';
COMMENT ON COLUMN coupon_template.updated_at IS '优惠券模板最后修改时间。';
CREATE INDEX coupon_template_claim_idx ON coupon_template (status, claim_starts_at, claim_ends_at);

CREATE TABLE operator_audit_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    operator_id BIGINT NOT NULL REFERENCES app_user(id),
    target_type VARCHAR(32) NOT NULL CHECK (target_type IN ('PRODUCT', 'ACTIVITY', 'COUPON_TEMPLATE', 'USER_ACCOUNT')),
    target_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL CHECK (action IN ('CREATE', 'UPDATE', 'ON_SALE', 'OFF_SALE', 'CANCEL', 'PAUSE', 'RESUME', 'CREATE_ACCOUNT', 'UPDATE_ROLE', 'ENABLE_ACCOUNT', 'DISABLE_ACCOUNT')),
    before_snapshot JSONB,
    after_snapshot JSONB,
    trace_id VARCHAR(128),
    request_source VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE operator_audit_log IS '运营业务配置和管理员账户权限变更的不可变审计记录。';
COMMENT ON COLUMN operator_audit_log.id IS '审计记录主键。';
COMMENT ON COLUMN operator_audit_log.operator_id IS '执行操作的 OPERATOR 或 ADMIN 账户。';
COMMENT ON COLUMN operator_audit_log.target_type IS '被操作对象类型：PRODUCT 商品、ACTIVITY 活动、COUPON_TEMPLATE 优惠券模板、USER_ACCOUNT 账户。';
COMMENT ON COLUMN operator_audit_log.target_id IS '被操作对象主键。';
COMMENT ON COLUMN operator_audit_log.action IS '操作类型：CREATE/UPDATE/ON_SALE/OFF_SALE/CANCEL/PAUSE/RESUME 或账户管理操作。';
COMMENT ON COLUMN operator_audit_log.before_snapshot IS '变更前的对象 JSON 快照；创建操作可为空。';
COMMENT ON COLUMN operator_audit_log.after_snapshot IS '变更后的对象 JSON 快照。';
COMMENT ON COLUMN operator_audit_log.trace_id IS '请求的分布式链路标识。';
COMMENT ON COLUMN operator_audit_log.request_source IS '请求来源，例如后台用户标识或调用方地址。';
COMMENT ON COLUMN operator_audit_log.created_at IS '操作审计创建时间。';
CREATE INDEX operator_audit_log_operator_created_idx ON operator_audit_log (operator_id, created_at DESC);
CREATE INDEX operator_audit_log_target_created_idx ON operator_audit_log (target_type, target_id, created_at DESC);
CREATE INDEX operator_audit_log_trace_idx ON operator_audit_log (trace_id) WHERE trace_id IS NOT NULL;

CREATE TABLE user_coupon (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    coupon_template_id BIGINT NOT NULL REFERENCES coupon_template(id),
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    status user_coupon_status NOT NULL DEFAULT 'AVAILABLE',
    claimed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reserved_at TIMESTAMPTZ,
    consumed_at TIMESTAMPTZ,
    expired_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (id, user_id)
);
COMMENT ON TABLE user_coupon IS '用户实际领取的一张优惠券实例。';
COMMENT ON COLUMN user_coupon.id IS '用户优惠券实例主键，一张券对应一条记录。';
COMMENT ON COLUMN user_coupon.coupon_template_id IS '所属优惠券模板。';
COMMENT ON COLUMN user_coupon.user_id IS '领取该优惠券的用户。';
COMMENT ON COLUMN user_coupon.status IS '该用户券当前状态。';
COMMENT ON COLUMN user_coupon.claimed_at IS '用户成功领取时间。';
COMMENT ON COLUMN user_coupon.reserved_at IS '该券被待支付订单锁定的时间。';
COMMENT ON COLUMN user_coupon.consumed_at IS '该券支付成功并核销的时间。';
COMMENT ON COLUMN user_coupon.expired_at IS '该券被标记为过期的时间。';
COMMENT ON COLUMN user_coupon.version IS '用户券状态的乐观锁版本号，防止并发核销/释放。';
COMMENT ON COLUMN user_coupon.created_at IS '用户券记录创建时间。';
COMMENT ON COLUMN user_coupon.updated_at IS '用户券记录最后修改时间。';
CREATE INDEX user_coupon_user_status_idx ON user_coupon (user_id, status, id);
CREATE INDEX user_coupon_template_user_idx ON user_coupon (coupon_template_id, user_id);

-- Fast, lockable counters used when a user claims a coupon or reserves an activity quota.
CREATE TABLE coupon_user_claim_counter (
    coupon_template_id BIGINT NOT NULL REFERENCES coupon_template(id),
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    claimed_count INTEGER NOT NULL DEFAULT 0 CHECK (claimed_count >= 0),
    PRIMARY KEY (coupon_template_id, user_id)
);
COMMENT ON TABLE coupon_user_claim_counter IS '用户针对优惠券模板的领取数量计数器，用于并发限领。';
COMMENT ON COLUMN coupon_user_claim_counter.coupon_template_id IS '被计数的优惠券模板。';
COMMENT ON COLUMN coupon_user_claim_counter.user_id IS '被计数的用户。';
COMMENT ON COLUMN coupon_user_claim_counter.claimed_count IS '该用户已领取该模板的数量，用于原子限领。';

CREATE TABLE activity_user_quota (
    activity_id BIGINT NOT NULL REFERENCES marketing_activity(id),
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    committed_quantity INTEGER NOT NULL DEFAULT 0 CHECK (committed_quantity >= 0),
    PRIMARY KEY (activity_id, user_id)
);
COMMENT ON TABLE activity_user_quota IS '用户针对活动的已承诺购买数量，用于并发限购。';
COMMENT ON COLUMN activity_user_quota.activity_id IS '被计数的营销活动。';
COMMENT ON COLUMN activity_user_quota.user_id IS '被计数的用户。';
COMMENT ON COLUMN activity_user_quota.committed_quantity IS '该用户在活动中已承诺的购买数量，包含待支付和已支付订单，取消后释放。';

CREATE TABLE activity_inventory_checkpoint (
    activity_id BIGINT PRIMARY KEY REFERENCES marketing_activity(id),
    last_contiguous_sequence BIGINT NOT NULL DEFAULT 0 CHECK (last_contiguous_sequence >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE activity_inventory_checkpoint IS '活动库存事件消费者的连续处理检查点，用于暂停屏障追平和 Redis 丢失后的安全恢复。';
COMMENT ON COLUMN activity_inventory_checkpoint.activity_id IS '活动主键。';
COMMENT ON COLUMN activity_inventory_checkpoint.last_contiguous_sequence IS '库存事件已连续成功处理到的最大活动库存事件序号，包含预扣和释放，不能跨越缺失事件。';
COMMENT ON COLUMN activity_inventory_checkpoint.updated_at IS '检查点最后推进时间。';

CREATE TABLE activity_inventory_sequence (
    activity_id BIGINT PRIMARY KEY REFERENCES marketing_activity(id),
    next_event_sequence BIGINT NOT NULL DEFAULT 1 CHECK (next_event_sequence > 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE activity_inventory_sequence IS '活动库存事件序号分配器；活动预扣与释放在各自本地事务中锁定本行后取得连续序号。';
COMMENT ON COLUMN activity_inventory_sequence.activity_id IS '活动主键。';
COMMENT ON COLUMN activity_inventory_sequence.next_event_sequence IS '下一条已提交活动库存事件应分配的序号。';
COMMENT ON COLUMN activity_inventory_sequence.updated_at IS '序号分配器最后修改时间。';

CREATE TABLE activity_recovery_job (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    activity_id BIGINT NOT NULL REFERENCES marketing_activity(id),
    requested_by BIGINT NOT NULL REFERENCES app_user(id),
    recovery_barrier_sequence BIGINT CHECK (recovery_barrier_sequence IS NULL OR recovery_barrier_sequence >= 0),
    status activity_recovery_status NOT NULL DEFAULT 'PENDING',
    last_error TEXT,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (status = 'PENDING' OR recovery_barrier_sequence IS NOT NULL)
);
COMMENT ON TABLE activity_recovery_job IS '活动异步恢复任务；活动对外保持 PAUSED，只有库存事件、Outbox、对账和预热全部完成后才激活。';
COMMENT ON COLUMN activity_recovery_job.id IS '恢复任务主键。';
COMMENT ON COLUMN activity_recovery_job.activity_id IS '待恢复的活动。';
COMMENT ON COLUMN activity_recovery_job.requested_by IS '请求恢复的运营人员。';
COMMENT ON COLUMN activity_recovery_job.recovery_barrier_sequence IS '恢复任务进入 RUNNING 并取得活动互斥锁后截取的活动库存事件屏障；PENDING 时为空，所有不大于该序号的事件必须已发送且被连续消费。';
COMMENT ON COLUMN activity_recovery_job.status IS '异步恢复任务状态。';
COMMENT ON COLUMN activity_recovery_job.last_error IS '最近一次恢复失败原因。';
COMMENT ON COLUMN activity_recovery_job.requested_at IS '运营人员请求恢复时间。';
COMMENT ON COLUMN activity_recovery_job.started_at IS '后台任务开始恢复时间。';
COMMENT ON COLUMN activity_recovery_job.completed_at IS '恢复成功或最终失败完成时间。';
COMMENT ON COLUMN activity_recovery_job.updated_at IS '恢复任务最后修改时间。';
CREATE UNIQUE INDEX activity_recovery_job_active_uk ON activity_recovery_job (activity_id) WHERE status IN ('PENDING', 'RUNNING');

CREATE TABLE customer_order (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_number VARCHAR(40) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    kind order_kind NOT NULL,
    activity_id BIGINT REFERENCES marketing_activity(id),
    status order_status NOT NULL DEFAULT 'PENDING_PAYMENT',
    item_subtotal_minor BIGINT NOT NULL CHECK (item_subtotal_minor >= 0),
    activity_discount_minor BIGINT NOT NULL DEFAULT 0 CHECK (activity_discount_minor >= 0),
    coupon_discount_minor BIGINT NOT NULL DEFAULT 0 CHECK (coupon_discount_minor >= 0),
    payable_amount_minor BIGINT NOT NULL CHECK (payable_amount_minor >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    expires_at TIMESTAMPTZ NOT NULL,
    paid_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    cancellation_reason VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((kind = 'ACTIVITY' AND activity_id IS NOT NULL) OR
           (kind = 'DIRECT' AND activity_id IS NULL)),
    CHECK (expires_at > created_at),
    CHECK (payable_amount_minor = item_subtotal_minor - activity_discount_minor - coupon_discount_minor),
    CHECK (coupon_discount_minor <= item_subtotal_minor - activity_discount_minor),
    UNIQUE (id, user_id)
);
COMMENT ON TABLE customer_order IS '客户订单头；支持普通直接订单和单商品限时活动订单。';
COMMENT ON COLUMN customer_order.id IS '订单主键。';
COMMENT ON COLUMN customer_order.order_number IS '对外展示的订单编号。';
COMMENT ON COLUMN customer_order.user_id IS '下单用户。';
COMMENT ON COLUMN customer_order.kind IS '订单类型：普通直接订单或限时活动订单。';
COMMENT ON COLUMN customer_order.activity_id IS '活动订单关联的活动；普通直接订单必须为空。';
COMMENT ON COLUMN customer_order.status IS '订单生命周期状态。';
COMMENT ON COLUMN customer_order.item_subtotal_minor IS '订单商品原始金额合计，未扣活动优惠和优惠券。';
COMMENT ON COLUMN customer_order.activity_discount_minor IS '活动优惠金额合计，使用最小货币单位整数保存。';
COMMENT ON COLUMN customer_order.coupon_discount_minor IS '优惠券减免金额，使用最小货币单位整数保存。';
COMMENT ON COLUMN customer_order.payable_amount_minor IS '订单最终应支付金额。';
COMMENT ON COLUMN customer_order.currency IS '金额币种，ISO 4217 三位代码。';
COMMENT ON COLUMN customer_order.expires_at IS '待支付订单的自动取消时间，创建后默认 15 分钟。';
COMMENT ON COLUMN customer_order.paid_at IS '订单支付成功时间。';
COMMENT ON COLUMN customer_order.cancelled_at IS '订单取消时间。';
COMMENT ON COLUMN customer_order.cancellation_reason IS '取消原因编码，例如 USER_CANCEL 或 PAYMENT_TIMEOUT。';
COMMENT ON COLUMN customer_order.created_at IS '订单创建时间。';
COMMENT ON COLUMN customer_order.updated_at IS '订单最后修改时间。';
CREATE INDEX customer_order_user_created_idx ON customer_order (user_id, created_at DESC);
CREATE INDEX customer_order_pending_expiry_idx ON customer_order (expires_at) WHERE status = 'PENDING_PAYMENT';
CREATE INDEX customer_order_activity_idx ON customer_order (activity_id) WHERE activity_id IS NOT NULL;

CREATE TABLE order_item (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES customer_order(id) ON DELETE RESTRICT,
    product_id BIGINT NOT NULL REFERENCES product(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    sku_snapshot VARCHAR(64) NOT NULL,
    product_name_snapshot VARCHAR(200) NOT NULL,
    list_price_minor BIGINT NOT NULL CHECK (list_price_minor >= 0),
    sale_price_minor BIGINT NOT NULL CHECK (sale_price_minor >= 0),
    activity_discount_minor BIGINT NOT NULL DEFAULT 0 CHECK (activity_discount_minor >= 0),
    line_amount_minor BIGINT NOT NULL CHECK (line_amount_minor >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (sale_price_minor <= list_price_minor),
    CHECK (activity_discount_minor = (list_price_minor - sale_price_minor) * quantity),
    CHECK (line_amount_minor = sale_price_minor * quantity)
);
COMMENT ON TABLE order_item IS '订单商品行及创建订单时保存的商品、价格快照。';
COMMENT ON COLUMN order_item.id IS '订单项主键。';
COMMENT ON COLUMN order_item.order_id IS '所属订单。';
COMMENT ON COLUMN order_item.product_id IS '购买时关联的商品主键。';
COMMENT ON COLUMN order_item.quantity IS '购买数量。';
COMMENT ON COLUMN order_item.sku_snapshot IS '下单时保存的 SKU 快照，避免商品后续修改影响历史订单。';
COMMENT ON COLUMN order_item.product_name_snapshot IS '下单时保存的商品名称快照。';
COMMENT ON COLUMN order_item.list_price_minor IS '下单时商品标准价快照。';
COMMENT ON COLUMN order_item.sale_price_minor IS '下单时实际商品单价快照；活动订单为活动价，直接订单为普通售价。';
COMMENT ON COLUMN order_item.activity_discount_minor IS '该订单项的活动优惠总额，不是单价。';
COMMENT ON COLUMN order_item.line_amount_minor IS '该订单项实际金额，等于 sale_price_minor 乘以 quantity。';
COMMENT ON COLUMN order_item.created_at IS '订单项创建时间。';
CREATE INDEX order_item_order_idx ON order_item (order_id);
CREATE INDEX order_item_product_idx ON order_item (product_id);

CREATE TABLE inventory_reservation (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_item_id BIGINT NOT NULL UNIQUE REFERENCES order_item(id) ON DELETE RESTRICT,
    source inventory_source NOT NULL,
    product_id BIGINT REFERENCES product(id),
    activity_id BIGINT REFERENCES marketing_activity(id),
    activity_reserve_sequence BIGINT,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    status reservation_status NOT NULL DEFAULT 'RESERVED',
    reserved_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmed_at TIMESTAMPTZ,
    released_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((source = 'PRODUCT' AND product_id IS NOT NULL AND activity_id IS NULL AND activity_reserve_sequence IS NULL) OR
           (source = 'ACTIVITY' AND activity_id IS NOT NULL AND product_id IS NULL AND activity_reserve_sequence IS NOT NULL AND activity_reserve_sequence > 0))
);
COMMENT ON TABLE inventory_reservation IS '订单项对应的库存保留记录；每个订单项最多一条。';
COMMENT ON COLUMN inventory_reservation.id IS '库存保留记录主键。';
COMMENT ON COLUMN inventory_reservation.order_item_id IS '被该记录保留库存的订单项；一个订单项最多一条保留记录。';
COMMENT ON COLUMN inventory_reservation.source IS '库存池类型：商品库存或活动独立库存。';
COMMENT ON COLUMN inventory_reservation.product_id IS '商品库存池标识；source 为 PRODUCT 时填写。';
COMMENT ON COLUMN inventory_reservation.activity_id IS '活动库存池标识；source 为 ACTIVITY 时填写。';
COMMENT ON COLUMN inventory_reservation.activity_reserve_sequence IS '活动 RESERVE 事件的连续序号；在订单本地事务锁定活动事件序号分配器后分配。';
COMMENT ON COLUMN inventory_reservation.quantity IS '本次订单项保留的库存数量。';
COMMENT ON COLUMN inventory_reservation.status IS '库存保留生命周期状态。';
COMMENT ON COLUMN inventory_reservation.reserved_at IS '库存成功保留时间。';
COMMENT ON COLUMN inventory_reservation.confirmed_at IS '支付成功后库存保留确认时间。';
COMMENT ON COLUMN inventory_reservation.released_at IS '订单取消或超时后库存释放时间。';
COMMENT ON COLUMN inventory_reservation.created_at IS '库存保留记录创建时间。';
COMMENT ON COLUMN inventory_reservation.updated_at IS '库存保留记录最后修改时间。';
CREATE INDEX inventory_reservation_active_idx ON inventory_reservation (source, activity_id, status) WHERE source = 'ACTIVITY';
CREATE INDEX inventory_reservation_product_idx ON inventory_reservation (source, product_id, status) WHERE source = 'PRODUCT';
CREATE UNIQUE INDEX inventory_reservation_activity_sequence_uk ON inventory_reservation (activity_id, activity_reserve_sequence) WHERE source = 'ACTIVITY';

CREATE TABLE inventory_movement (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id BIGINT REFERENCES inventory_reservation(id),
    source inventory_source NOT NULL,
    product_id BIGINT REFERENCES product(id),
    activity_id BIGINT REFERENCES marketing_activity(id),
    activity_inventory_event_sequence BIGINT,
    quantity_delta INTEGER NOT NULL CHECK (quantity_delta <> 0),
    reason inventory_movement_reason NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((source = 'PRODUCT' AND product_id IS NOT NULL AND activity_id IS NULL AND activity_inventory_event_sequence IS NULL) OR
           (source = 'ACTIVITY' AND activity_id IS NOT NULL AND product_id IS NULL AND activity_inventory_event_sequence IS NOT NULL AND activity_inventory_event_sequence > 0))
);
COMMENT ON TABLE inventory_movement IS '库存保留、释放、初始化和调整的不可变流水。';
COMMENT ON COLUMN inventory_movement.id IS '库存流水主键。';
COMMENT ON COLUMN inventory_movement.reservation_id IS '关联的库存保留记录；初始化或人工调整可为空。';
COMMENT ON COLUMN inventory_movement.source IS '发生变化的库存池类型。';
COMMENT ON COLUMN inventory_movement.product_id IS '商品库存标识；商品库存流水时填写。';
COMMENT ON COLUMN inventory_movement.activity_id IS '活动库存标识；活动库存流水时填写。';
COMMENT ON COLUMN inventory_movement.activity_inventory_event_sequence IS '活动库存事件的单调递增序号；预扣和释放事件都必须分配，用于暂停屏障和连续消费检查点。';
COMMENT ON COLUMN inventory_movement.quantity_delta IS '库存变化量；负数表示扣减/保留，正数表示释放/增加。';
COMMENT ON COLUMN inventory_movement.reason IS '库存变化原因。';
COMMENT ON COLUMN inventory_movement.occurred_at IS '库存变化发生时间。';
CREATE INDEX inventory_movement_reservation_idx ON inventory_movement (reservation_id, occurred_at);
CREATE UNIQUE INDEX inventory_movement_activity_sequence_uk ON inventory_movement (activity_id, activity_inventory_event_sequence) WHERE source = 'ACTIVITY';

CREATE TABLE activity_inventory_event (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    activity_id BIGINT NOT NULL REFERENCES marketing_activity(id),
    event_sequence BIGINT NOT NULL CHECK (event_sequence > 0),
    reservation_id BIGINT REFERENCES inventory_reservation(id),
    kind activity_inventory_event_kind NOT NULL,
    quantity_delta INTEGER NOT NULL CHECK (quantity_delta <> 0),
    producer VARCHAR(64) NOT NULL,
    outbox_event_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (activity_id, event_sequence),
    CHECK ((kind = 'RESERVE' AND quantity_delta < 0) OR
           (kind = 'RELEASE' AND quantity_delta > 0))
);
COMMENT ON TABLE activity_inventory_event IS '活动库存事件账本；预扣和释放在业务本地事务中与对应 Outbox 一起持久化，用于暂停/恢复栅栏与对账。';
COMMENT ON COLUMN activity_inventory_event.id IS '活动库存事件账本主键。';
COMMENT ON COLUMN activity_inventory_event.event_id IS '活动库存事件全局标识，也是 RocketMQ 消息事件标识。';
COMMENT ON COLUMN activity_inventory_event.activity_id IS '活动库存池所属活动。';
COMMENT ON COLUMN activity_inventory_event.event_sequence IS '活动内连续库存事件序号；由 activity_inventory_sequence 在成功本地事务内分配。';
COMMENT ON COLUMN activity_inventory_event.reservation_id IS '关联的库存保留；预扣和释放均关联原保留记录。';
COMMENT ON COLUMN activity_inventory_event.kind IS '库存事件类型：预扣或释放。';
COMMENT ON COLUMN activity_inventory_event.quantity_delta IS '实时库存变化量；预扣为负，释放为正。';
COMMENT ON COLUMN activity_inventory_event.producer IS '产生该事件的业务服务名称。';
COMMENT ON COLUMN activity_inventory_event.outbox_event_id IS '承载该事件的生产端 Outbox 事件号，用于恢复时检查是否已投递。';
COMMENT ON COLUMN activity_inventory_event.created_at IS '库存事件在业务本地事务中提交的创建时间。';
CREATE INDEX activity_inventory_event_barrier_idx ON activity_inventory_event (activity_id, event_sequence);

CREATE TABLE coupon_reservation (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    user_coupon_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    threshold_snapshot_minor BIGINT NOT NULL CHECK (threshold_snapshot_minor >= 0),
    discount_snapshot_minor BIGINT NOT NULL CHECK (discount_snapshot_minor > 0),
    status reservation_status NOT NULL DEFAULT 'RESERVED',
    reserved_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    consumed_at TIMESTAMPTZ,
    released_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (order_id, user_id) REFERENCES customer_order (id, user_id),
    FOREIGN KEY (user_coupon_id, user_id) REFERENCES user_coupon (id, user_id)
);
COMMENT ON TABLE coupon_reservation IS '订单锁定或核销用户优惠券的记录。';
COMMENT ON COLUMN coupon_reservation.id IS '优惠券保留记录主键。';
COMMENT ON COLUMN coupon_reservation.order_id IS '锁定该优惠券的订单；一个订单最多一张券。';
COMMENT ON COLUMN coupon_reservation.user_coupon_id IS '被锁定或核销的用户优惠券实例。';
COMMENT ON COLUMN coupon_reservation.user_id IS '订单和用户券共同所属的用户，用于复合外键校验。';
COMMENT ON COLUMN coupon_reservation.threshold_snapshot_minor IS '下单时保存的优惠券使用门槛快照。';
COMMENT ON COLUMN coupon_reservation.discount_snapshot_minor IS '下单时保存的优惠券减免金额快照。';
COMMENT ON COLUMN coupon_reservation.status IS '优惠券保留生命周期状态。';
COMMENT ON COLUMN coupon_reservation.reserved_at IS '优惠券被订单锁定时间。';
COMMENT ON COLUMN coupon_reservation.consumed_at IS '支付成功后优惠券核销时间。';
COMMENT ON COLUMN coupon_reservation.released_at IS '订单取消/超时后优惠券释放时间。';
COMMENT ON COLUMN coupon_reservation.created_at IS '优惠券保留记录创建时间。';
COMMENT ON COLUMN coupon_reservation.updated_at IS '优惠券保留记录最后修改时间。';
-- A coupon can be reserved by at most one live order and can be consumed once.
CREATE UNIQUE INDEX coupon_reservation_live_coupon_uk
    ON coupon_reservation (user_coupon_id)
    WHERE status IN ('RESERVED', 'CONFIRMED');

CREATE TABLE order_submission_idempotency (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    status idempotency_status NOT NULL DEFAULT 'PROCESSING',
    order_id BIGINT UNIQUE REFERENCES customer_order(id),
    response_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    UNIQUE (user_id, idempotency_key)
);
COMMENT ON TABLE order_submission_idempotency IS '用户提交订单请求的幂等键及首次处理结果。';
COMMENT ON COLUMN order_submission_idempotency.id IS '下单幂等记录主键。';
COMMENT ON COLUMN order_submission_idempotency.user_id IS '发起下单请求的用户。';
COMMENT ON COLUMN order_submission_idempotency.idempotency_key IS '客户端为一次业务提交生成的幂等键。';
COMMENT ON COLUMN order_submission_idempotency.request_fingerprint IS '请求参数规范化后的 SHA-256 指纹，用于拒绝同键不同请求。';
COMMENT ON COLUMN order_submission_idempotency.status IS '幂等请求的处理状态。';
COMMENT ON COLUMN order_submission_idempotency.order_id IS '成功创建时关联的订单。';
COMMENT ON COLUMN order_submission_idempotency.response_code IS '首次处理结果编码，供重试请求复用。';
COMMENT ON COLUMN order_submission_idempotency.created_at IS '首次收到该幂等请求的时间。';
COMMENT ON COLUMN order_submission_idempotency.completed_at IS '幂等请求处理完成时间。';

CREATE TABLE coupon_claim_idempotency (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    coupon_template_id BIGINT NOT NULL REFERENCES coupon_template(id),
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    status idempotency_status NOT NULL DEFAULT 'PROCESSING',
    user_coupon_id BIGINT REFERENCES user_coupon(id),
    response_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    UNIQUE (user_id, coupon_template_id, idempotency_key)
);
COMMENT ON TABLE coupon_claim_idempotency IS '用户领取优惠券请求的幂等记录，防止重复领取并复用首次结果。';
COMMENT ON COLUMN coupon_claim_idempotency.id IS '领券幂等记录主键。';
COMMENT ON COLUMN coupon_claim_idempotency.user_id IS '发起领取请求的消费者。';
COMMENT ON COLUMN coupon_claim_idempotency.coupon_template_id IS '本次领取对应的优惠券模板。';
COMMENT ON COLUMN coupon_claim_idempotency.idempotency_key IS '客户端为一次领券请求生成的幂等键。';
COMMENT ON COLUMN coupon_claim_idempotency.request_fingerprint IS '领取请求参数规范化后的 SHA-256 指纹。';
COMMENT ON COLUMN coupon_claim_idempotency.status IS '领券请求处理状态。';
COMMENT ON COLUMN coupon_claim_idempotency.user_coupon_id IS '成功领取时生成的用户券实例。';
COMMENT ON COLUMN coupon_claim_idempotency.response_code IS '首次处理结果编码，供重试请求复用。';
COMMENT ON COLUMN coupon_claim_idempotency.created_at IS '首次收到领券请求的时间。';
COMMENT ON COLUMN coupon_claim_idempotency.completed_at IS '领券请求处理完成时间。';

CREATE TABLE payment_record (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE REFERENCES customer_order(id) ON DELETE RESTRICT,
    status payment_status NOT NULL DEFAULT 'PENDING',
    amount_minor BIGINT NOT NULL CHECK (amount_minor >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    provider_transaction_id VARCHAR(128) UNIQUE,
    paid_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    failure_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE payment_record IS '订单模拟支付记录；每个订单最多一条有效支付记录。';
COMMENT ON COLUMN payment_record.id IS '支付记录主键。';
COMMENT ON COLUMN payment_record.order_id IS '被支付的订单；一个订单最多一条支付记录。';
COMMENT ON COLUMN payment_record.status IS '模拟支付处理状态。';
COMMENT ON COLUMN payment_record.amount_minor IS '本次支付金额，必须与订单应付金额一致。';
COMMENT ON COLUMN payment_record.currency IS '支付币种，ISO 4217 三位代码。';
COMMENT ON COLUMN payment_record.provider_transaction_id IS '模拟支付渠道返回的交易流水号。';
COMMENT ON COLUMN payment_record.paid_at IS '支付成功时间。';
COMMENT ON COLUMN payment_record.failed_at IS '支付失败时间。';
COMMENT ON COLUMN payment_record.failure_code IS '支付失败原因编码。';
COMMENT ON COLUMN payment_record.created_at IS '支付记录创建时间。';
COMMENT ON COLUMN payment_record.updated_at IS '支付记录最后修改时间。';

CREATE TABLE payment_submission_idempotency (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    order_id BIGINT NOT NULL REFERENCES customer_order(id),
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    status idempotency_status NOT NULL DEFAULT 'PROCESSING',
    payment_record_id BIGINT REFERENCES payment_record(id),
    response_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    UNIQUE (user_id, order_id, idempotency_key)
);
COMMENT ON TABLE payment_submission_idempotency IS '用户发起支付请求的幂等记录，防止重复支付并复用首次结果。';
COMMENT ON COLUMN payment_submission_idempotency.id IS '支付请求幂等记录主键。';
COMMENT ON COLUMN payment_submission_idempotency.user_id IS '发起支付请求的消费者。';
COMMENT ON COLUMN payment_submission_idempotency.order_id IS '本次支付对应的订单。';
COMMENT ON COLUMN payment_submission_idempotency.idempotency_key IS '客户端为一次支付请求生成的幂等键。';
COMMENT ON COLUMN payment_submission_idempotency.request_fingerprint IS '支付请求参数规范化后的 SHA-256 指纹。';
COMMENT ON COLUMN payment_submission_idempotency.status IS '支付请求处理状态。';
COMMENT ON COLUMN payment_submission_idempotency.payment_record_id IS '成功处理时关联的支付记录。';
COMMENT ON COLUMN payment_submission_idempotency.response_code IS '首次处理结果编码，供重试请求复用。';
COMMENT ON COLUMN payment_submission_idempotency.created_at IS '首次收到支付请求的时间。';
COMMENT ON COLUMN payment_submission_idempotency.completed_at IS '支付请求处理完成时间。';

CREATE TABLE fulfillment_record (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE REFERENCES customer_order(id) ON DELETE RESTRICT,
    status fulfillment_status NOT NULL DEFAULT 'COMPLETED',
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE fulfillment_record IS '支付成功后自动生成的模拟履约完成记录。';
COMMENT ON COLUMN fulfillment_record.id IS '履约记录主键。';
COMMENT ON COLUMN fulfillment_record.order_id IS '被履约的订单；一个订单最多一条履约记录。';
COMMENT ON COLUMN fulfillment_record.status IS '模拟履约状态，当前阶段固定为 COMPLETED。';
COMMENT ON COLUMN fulfillment_record.completed_at IS '模拟履约完成时间。';
COMMENT ON COLUMN fulfillment_record.created_at IS '履约记录创建时间。';

-- Each producer service owns its own outbox table. The repeated structure keeps write ownership explicit.
CREATE TABLE order_outbox (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    trace_id VARCHAR(128),
    status outbox_status NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_until TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE order_outbox IS '订单服务本地消息表；订单事务与事件记录必须同一事务提交。';
COMMENT ON COLUMN order_outbox.id IS 'Outbox 记录主键。';
COMMENT ON COLUMN order_outbox.event_id IS '全局事件唯一标识。';
COMMENT ON COLUMN order_outbox.event_type IS 'RocketMQ 事件类型。';
COMMENT ON COLUMN order_outbox.idempotency_key IS '事件业务幂等号，必须带订单业务前缀。';
COMMENT ON COLUMN order_outbox.aggregate_type IS '事件聚合类型，例如 ORDER。';
COMMENT ON COLUMN order_outbox.aggregate_id IS '事件聚合根业务标识。';
COMMENT ON COLUMN order_outbox.payload IS '发送到 MQ 的 JSON 事件载荷。';
COMMENT ON COLUMN order_outbox.trace_id IS '创建事件时关联的分布式链路标识。';
COMMENT ON COLUMN order_outbox.status IS '本地消息投递状态。';
COMMENT ON COLUMN order_outbox.attempt_count IS '已尝试投递次数。';
COMMENT ON COLUMN order_outbox.available_at IS '允许下一次投递的时间，用于退避。';
COMMENT ON COLUMN order_outbox.locked_until IS '投递任务租约截止时间，防止多实例重复领取。';
COMMENT ON COLUMN order_outbox.last_error IS '最近一次投递错误信息。';
COMMENT ON COLUMN order_outbox.created_at IS 'Outbox 创建时间。';
COMMENT ON COLUMN order_outbox.sent_at IS '首次成功投递时间。';
COMMENT ON COLUMN order_outbox.updated_at IS 'Outbox 最后修改时间。';
CREATE INDEX order_outbox_pending_idx ON order_outbox (available_at, id) WHERE status IN ('PENDING', 'FAILED');

CREATE TABLE coupon_outbox (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    trace_id VARCHAR(128),
    status outbox_status NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_until TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE coupon_outbox IS '优惠券服务本地消息表；领券、锁券和恢复事件与业务事务同提交。';
COMMENT ON COLUMN coupon_outbox.id IS 'Outbox 记录主键。';
COMMENT ON COLUMN coupon_outbox.event_id IS '全局事件唯一标识。';
COMMENT ON COLUMN coupon_outbox.event_type IS 'RocketMQ 事件类型。';
COMMENT ON COLUMN coupon_outbox.idempotency_key IS '事件业务幂等号，必须带优惠券业务前缀。';
COMMENT ON COLUMN coupon_outbox.aggregate_type IS '事件聚合类型，例如 USER_COUPON。';
COMMENT ON COLUMN coupon_outbox.aggregate_id IS '事件聚合根业务标识。';
COMMENT ON COLUMN coupon_outbox.payload IS '发送到 MQ 的 JSON 事件载荷。';
COMMENT ON COLUMN coupon_outbox.trace_id IS '创建事件时关联的分布式链路标识。';
COMMENT ON COLUMN coupon_outbox.status IS '本地消息投递状态。';
COMMENT ON COLUMN coupon_outbox.attempt_count IS '已尝试投递次数。';
COMMENT ON COLUMN coupon_outbox.available_at IS '允许下一次投递的时间，用于退避。';
COMMENT ON COLUMN coupon_outbox.locked_until IS '投递任务租约截止时间。';
COMMENT ON COLUMN coupon_outbox.last_error IS '最近一次投递错误信息。';
COMMENT ON COLUMN coupon_outbox.created_at IS 'Outbox 创建时间。';
COMMENT ON COLUMN coupon_outbox.sent_at IS '首次成功投递时间。';
COMMENT ON COLUMN coupon_outbox.updated_at IS 'Outbox 最后修改时间。';
CREATE INDEX coupon_outbox_pending_idx ON coupon_outbox (available_at, id) WHERE status IN ('PENDING', 'FAILED');

CREATE TABLE inventory_outbox (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    trace_id VARCHAR(128),
    status outbox_status NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_until TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE inventory_outbox IS '库存服务本地消息表；库存预占、释放和确认事件与库存事务同提交。';
COMMENT ON COLUMN inventory_outbox.id IS 'Outbox 记录主键。';
COMMENT ON COLUMN inventory_outbox.event_id IS '全局事件唯一标识。';
COMMENT ON COLUMN inventory_outbox.event_type IS 'RocketMQ 事件类型。';
COMMENT ON COLUMN inventory_outbox.idempotency_key IS '事件业务幂等号，必须带库存业务前缀。';
COMMENT ON COLUMN inventory_outbox.aggregate_type IS '事件聚合类型，例如 INVENTORY_RESERVATION。';
COMMENT ON COLUMN inventory_outbox.aggregate_id IS '事件聚合根业务标识。';
COMMENT ON COLUMN inventory_outbox.payload IS '发送到 MQ 的 JSON 事件载荷。';
COMMENT ON COLUMN inventory_outbox.trace_id IS '创建事件时关联的分布式链路标识。';
COMMENT ON COLUMN inventory_outbox.status IS '本地消息投递状态。';
COMMENT ON COLUMN inventory_outbox.attempt_count IS '已尝试投递次数。';
COMMENT ON COLUMN inventory_outbox.available_at IS '允许下一次投递的时间，用于退避。';
COMMENT ON COLUMN inventory_outbox.locked_until IS '投递任务租约截止时间。';
COMMENT ON COLUMN inventory_outbox.last_error IS '最近一次投递错误信息。';
COMMENT ON COLUMN inventory_outbox.created_at IS 'Outbox 创建时间。';
COMMENT ON COLUMN inventory_outbox.sent_at IS '首次成功投递时间。';
COMMENT ON COLUMN inventory_outbox.updated_at IS 'Outbox 最后修改时间。';
CREATE INDEX inventory_outbox_pending_idx ON inventory_outbox (available_at, id) WHERE status IN ('PENDING', 'FAILED');

CREATE TABLE payment_outbox (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    trace_id VARCHAR(128),
    status outbox_status NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_until TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE payment_outbox IS '支付服务本地消息表；支付状态变更与支付事件同一事务提交。';
COMMENT ON COLUMN payment_outbox.id IS 'Outbox 记录主键。';
COMMENT ON COLUMN payment_outbox.event_id IS '全局事件唯一标识。';
COMMENT ON COLUMN payment_outbox.event_type IS 'RocketMQ 事件类型。';
COMMENT ON COLUMN payment_outbox.idempotency_key IS '事件业务幂等号，必须带支付业务前缀。';
COMMENT ON COLUMN payment_outbox.aggregate_type IS '事件聚合类型，例如 PAYMENT。';
COMMENT ON COLUMN payment_outbox.aggregate_id IS '事件聚合根业务标识。';
COMMENT ON COLUMN payment_outbox.payload IS '发送到 MQ 的 JSON 事件载荷。';
COMMENT ON COLUMN payment_outbox.trace_id IS '创建事件时关联的分布式链路标识。';
COMMENT ON COLUMN payment_outbox.status IS '本地消息投递状态。';
COMMENT ON COLUMN payment_outbox.attempt_count IS '已尝试投递次数。';
COMMENT ON COLUMN payment_outbox.available_at IS '允许下一次投递的时间，用于退避。';
COMMENT ON COLUMN payment_outbox.locked_until IS '投递任务租约截止时间。';
COMMENT ON COLUMN payment_outbox.last_error IS '最近一次投递错误信息。';
COMMENT ON COLUMN payment_outbox.created_at IS 'Outbox 创建时间。';
COMMENT ON COLUMN payment_outbox.sent_at IS '首次成功投递时间。';
COMMENT ON COLUMN payment_outbox.updated_at IS 'Outbox 最后修改时间。';
CREATE INDEX payment_outbox_pending_idx ON payment_outbox (available_at, id) WHERE status IN ('PENDING', 'FAILED');

CREATE TABLE product_outbox (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    trace_id VARCHAR(128),
    status outbox_status NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_until TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE product_outbox IS '商品服务本地消息表；商品配置变更与缓存失效事件同一事务提交。';
COMMENT ON COLUMN product_outbox.id IS 'Outbox 记录主键。';
COMMENT ON COLUMN product_outbox.event_id IS '全局事件唯一标识。';
COMMENT ON COLUMN product_outbox.event_type IS '事件类型，例如 PRODUCT_CACHE_INVALIDATE。';
COMMENT ON COLUMN product_outbox.idempotency_key IS '带 PRODUCT 前缀的业务幂等号。';
COMMENT ON COLUMN product_outbox.aggregate_type IS '事件聚合类型 PRODUCT。';
COMMENT ON COLUMN product_outbox.aggregate_id IS '商品主键。';
COMMENT ON COLUMN product_outbox.payload IS '事件 JSON 载荷，包含待失效缓存键和延时信息。';
COMMENT ON COLUMN product_outbox.trace_id IS '创建事件时关联的分布式链路标识。';
COMMENT ON COLUMN product_outbox.status IS '本地消息投递状态。';
COMMENT ON COLUMN product_outbox.attempt_count IS '已尝试投递次数。';
COMMENT ON COLUMN product_outbox.available_at IS '允许下一次投递的时间。';
COMMENT ON COLUMN product_outbox.locked_until IS '投递任务租约截止时间。';
COMMENT ON COLUMN product_outbox.last_error IS '最近一次投递错误信息。';
COMMENT ON COLUMN product_outbox.created_at IS 'Outbox 创建时间。';
COMMENT ON COLUMN product_outbox.sent_at IS '首次成功投递时间。';
COMMENT ON COLUMN product_outbox.updated_at IS 'Outbox 最后修改时间。';
CREATE INDEX product_outbox_pending_idx ON product_outbox (available_at, id) WHERE status IN ('PENDING', 'FAILED');

CREATE TABLE activity_outbox (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    trace_id VARCHAR(128),
    status outbox_status NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_until TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE activity_outbox IS '活动服务本地消息表；活动配置变更与缓存失效事件同一事务提交。';
COMMENT ON COLUMN activity_outbox.id IS 'Outbox 记录主键。';
COMMENT ON COLUMN activity_outbox.event_id IS '全局事件唯一标识。';
COMMENT ON COLUMN activity_outbox.event_type IS '事件类型，例如 ACTIVITY_CACHE_INVALIDATE。';
COMMENT ON COLUMN activity_outbox.idempotency_key IS '带 ACTIVITY 前缀的业务幂等号。';
COMMENT ON COLUMN activity_outbox.aggregate_type IS '事件聚合类型 ACTIVITY。';
COMMENT ON COLUMN activity_outbox.aggregate_id IS '活动主键。';
COMMENT ON COLUMN activity_outbox.payload IS '事件 JSON 载荷，包含待失效缓存键和延时信息。';
COMMENT ON COLUMN activity_outbox.trace_id IS '创建事件时关联的分布式链路标识。';
COMMENT ON COLUMN activity_outbox.status IS '本地消息投递状态。';
COMMENT ON COLUMN activity_outbox.attempt_count IS '已尝试投递次数。';
COMMENT ON COLUMN activity_outbox.available_at IS '允许下一次投递的时间。';
COMMENT ON COLUMN activity_outbox.locked_until IS '投递任务租约截止时间。';
COMMENT ON COLUMN activity_outbox.last_error IS '最近一次投递错误信息。';
COMMENT ON COLUMN activity_outbox.created_at IS 'Outbox 创建时间。';
COMMENT ON COLUMN activity_outbox.sent_at IS '首次成功投递时间。';
COMMENT ON COLUMN activity_outbox.updated_at IS 'Outbox 最后修改时间。';
CREATE INDEX activity_outbox_pending_idx ON activity_outbox (available_at, id) WHERE status IN ('PENDING', 'FAILED');

CREATE TABLE order_message_idempotency (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    event_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    status message_consumer_status NOT NULL DEFAULT 'PROCESSING',
    trace_id VARCHAR(128),
    attempt_count INTEGER NOT NULL DEFAULT 1 CHECK (attempt_count > 0),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE order_message_idempotency IS '订单服务消费者幂等记录；按业务幂等号防止重复处理订单事件。';
COMMENT ON COLUMN order_message_idempotency.id IS '消费幂等记录主键。';
COMMENT ON COLUMN order_message_idempotency.idempotency_key IS '带 ORDER 前缀的业务幂等号。';
COMMENT ON COLUMN order_message_idempotency.event_id IS '被消费的全局事件号。';
COMMENT ON COLUMN order_message_idempotency.event_type IS '被消费的事件类型。';
COMMENT ON COLUMN order_message_idempotency.aggregate_id IS '事件聚合业务标识。';
COMMENT ON COLUMN order_message_idempotency.status IS '消费者处理状态。';
COMMENT ON COLUMN order_message_idempotency.trace_id IS '消费链路标识。';
COMMENT ON COLUMN order_message_idempotency.attempt_count IS '消费尝试次数。';
COMMENT ON COLUMN order_message_idempotency.started_at IS '本次处理开始时间。';
COMMENT ON COLUMN order_message_idempotency.completed_at IS '处理成功或最终失败时间。';
COMMENT ON COLUMN order_message_idempotency.last_error IS '最近一次消费错误。';
COMMENT ON COLUMN order_message_idempotency.created_at IS '幂等记录创建时间。';
COMMENT ON COLUMN order_message_idempotency.updated_at IS '幂等记录最后修改时间。';
CREATE INDEX order_message_idempotency_recovery_idx ON order_message_idempotency (started_at) WHERE status = 'PROCESSING';

CREATE TABLE coupon_message_idempotency (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    event_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    status message_consumer_status NOT NULL DEFAULT 'PROCESSING',
    trace_id VARCHAR(128),
    attempt_count INTEGER NOT NULL DEFAULT 1 CHECK (attempt_count > 0),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE coupon_message_idempotency IS '优惠券服务消费者幂等记录；按业务幂等号防止重复处理优惠券事件。';
COMMENT ON COLUMN coupon_message_idempotency.id IS '消费幂等记录主键。';
COMMENT ON COLUMN coupon_message_idempotency.idempotency_key IS '带 COUPON 前缀的业务幂等号。';
COMMENT ON COLUMN coupon_message_idempotency.event_id IS '被消费的全局事件号。';
COMMENT ON COLUMN coupon_message_idempotency.event_type IS '被消费的事件类型。';
COMMENT ON COLUMN coupon_message_idempotency.aggregate_id IS '事件聚合业务标识。';
COMMENT ON COLUMN coupon_message_idempotency.status IS '消费者处理状态。';
COMMENT ON COLUMN coupon_message_idempotency.trace_id IS '消费链路标识。';
COMMENT ON COLUMN coupon_message_idempotency.attempt_count IS '消费尝试次数。';
COMMENT ON COLUMN coupon_message_idempotency.started_at IS '本次处理开始时间。';
COMMENT ON COLUMN coupon_message_idempotency.completed_at IS '处理成功或最终失败时间。';
COMMENT ON COLUMN coupon_message_idempotency.last_error IS '最近一次消费错误。';
COMMENT ON COLUMN coupon_message_idempotency.created_at IS '幂等记录创建时间。';
COMMENT ON COLUMN coupon_message_idempotency.updated_at IS '幂等记录最后修改时间。';
CREATE INDEX coupon_message_idempotency_recovery_idx ON coupon_message_idempotency (started_at) WHERE status = 'PROCESSING';

CREATE TABLE inventory_message_idempotency (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    event_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    status message_consumer_status NOT NULL DEFAULT 'PROCESSING',
    trace_id VARCHAR(128),
    attempt_count INTEGER NOT NULL DEFAULT 1 CHECK (attempt_count > 0),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE inventory_message_idempotency IS '库存服务消费者幂等记录；按业务幂等号防止重复处理库存事件。';
COMMENT ON COLUMN inventory_message_idempotency.id IS '消费幂等记录主键。';
COMMENT ON COLUMN inventory_message_idempotency.idempotency_key IS '带 STOCK 前缀的业务幂等号。';
COMMENT ON COLUMN inventory_message_idempotency.event_id IS '被消费的全局事件号。';
COMMENT ON COLUMN inventory_message_idempotency.event_type IS '被消费的事件类型。';
COMMENT ON COLUMN inventory_message_idempotency.aggregate_id IS '事件聚合业务标识。';
COMMENT ON COLUMN inventory_message_idempotency.status IS '消费者处理状态。';
COMMENT ON COLUMN inventory_message_idempotency.trace_id IS '消费链路标识。';
COMMENT ON COLUMN inventory_message_idempotency.attempt_count IS '消费尝试次数。';
COMMENT ON COLUMN inventory_message_idempotency.started_at IS '本次处理开始时间。';
COMMENT ON COLUMN inventory_message_idempotency.completed_at IS '处理成功或最终失败时间。';
COMMENT ON COLUMN inventory_message_idempotency.last_error IS '最近一次消费错误。';
COMMENT ON COLUMN inventory_message_idempotency.created_at IS '幂等记录创建时间。';
COMMENT ON COLUMN inventory_message_idempotency.updated_at IS '幂等记录最后修改时间。';
CREATE INDEX inventory_message_idempotency_recovery_idx ON inventory_message_idempotency (started_at) WHERE status = 'PROCESSING';

CREATE TABLE payment_message_idempotency (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    event_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    status message_consumer_status NOT NULL DEFAULT 'PROCESSING',
    trace_id VARCHAR(128),
    attempt_count INTEGER NOT NULL DEFAULT 1 CHECK (attempt_count > 0),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE payment_message_idempotency IS '支付服务消费者幂等记录；按业务幂等号防止重复处理支付事件。';
COMMENT ON COLUMN payment_message_idempotency.id IS '消费幂等记录主键。';
COMMENT ON COLUMN payment_message_idempotency.idempotency_key IS '带 PAYMENT 前缀的业务幂等号。';
COMMENT ON COLUMN payment_message_idempotency.event_id IS '被消费的全局事件号。';
COMMENT ON COLUMN payment_message_idempotency.event_type IS '被消费的事件类型。';
COMMENT ON COLUMN payment_message_idempotency.aggregate_id IS '事件聚合业务标识。';
COMMENT ON COLUMN payment_message_idempotency.status IS '消费者处理状态。';
COMMENT ON COLUMN payment_message_idempotency.trace_id IS '消费链路标识。';
COMMENT ON COLUMN payment_message_idempotency.attempt_count IS '消费尝试次数。';
COMMENT ON COLUMN payment_message_idempotency.started_at IS '本次处理开始时间。';
COMMENT ON COLUMN payment_message_idempotency.completed_at IS '处理成功或最终失败时间。';
COMMENT ON COLUMN payment_message_idempotency.last_error IS '最近一次消费错误。';
COMMENT ON COLUMN payment_message_idempotency.created_at IS '幂等记录创建时间。';
COMMENT ON COLUMN payment_message_idempotency.updated_at IS '幂等记录最后修改时间。';
CREATE INDEX payment_message_idempotency_recovery_idx ON payment_message_idempotency (started_at) WHERE status = 'PROCESSING';

CREATE OR REPLACE FUNCTION touch_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION enforce_operator_actor() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    actor_id BIGINT;
    actor_role user_role;
    actor_status VARCHAR(24);
BEGIN
    IF TG_OP = 'INSERT' THEN
        actor_id := (to_jsonb(NEW) ->> 'created_by')::BIGINT;
        IF (to_jsonb(NEW) ->> 'updated_by')::BIGINT <> actor_id THEN
            RAISE EXCEPTION 'created_by and updated_by must match when creating %', TG_TABLE_NAME;
        END IF;
    ELSE
        IF (to_jsonb(NEW) ->> 'created_by')::BIGINT <> (to_jsonb(OLD) ->> 'created_by')::BIGINT THEN
            RAISE EXCEPTION 'created_by cannot be changed for %', TG_TABLE_NAME;
        END IF;
        actor_id := (to_jsonb(NEW) ->> 'updated_by')::BIGINT;
    END IF;
    SELECT role, status INTO actor_role, actor_status FROM app_user WHERE id = actor_id;
    IF actor_role IS DISTINCT FROM 'OPERATOR' OR actor_status IS DISTINCT FROM 'ACTIVE' THEN
        RAISE EXCEPTION 'business configuration actor % must be an active OPERATOR', actor_id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION enforce_audit_actor() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    actor_role user_role;
    actor_status VARCHAR(24);
BEGIN
    SELECT role, status INTO actor_role, actor_status FROM app_user WHERE id = NEW.operator_id;
    IF actor_status IS DISTINCT FROM 'ACTIVE' THEN
        RAISE EXCEPTION 'audit actor % must be active', NEW.operator_id;
    END IF;
    IF NEW.target_type = 'USER_ACCOUNT' AND actor_role IS DISTINCT FROM 'ADMIN' THEN
        RAISE EXCEPTION 'only ADMIN may audit account management actions';
    END IF;
    IF NEW.target_type IN ('PRODUCT', 'ACTIVITY', 'COUPON_TEMPLATE') AND actor_role IS DISTINCT FROM 'OPERATOR' THEN
        RAISE EXCEPTION 'only OPERATOR may audit business configuration actions';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION prevent_last_admin_removal() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    active_admin_count INTEGER;
BEGIN
    IF OLD.role = 'ADMIN' AND OLD.status = 'ACTIVE'
       AND (TG_OP = 'DELETE' OR NEW.role <> 'ADMIN' OR NEW.status <> 'ACTIVE') THEN
        SELECT count(*) INTO active_admin_count
          FROM app_user
         WHERE role = 'ADMIN' AND status = 'ACTIVE';
        IF active_admin_count <= 1 THEN
            RAISE EXCEPTION 'cannot remove or disable the last active ADMIN account';
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION enforce_order_transition() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status = OLD.status THEN RETURN NEW; END IF;
    IF (OLD.status = 'PENDING_PAYMENT' AND NEW.status IN ('PAID', 'CANCELLED'))
       OR (OLD.status = 'PAID' AND NEW.status = 'COMPLETED') THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'illegal order status transition: % -> %', OLD.status, NEW.status;
END;
$$;

CREATE OR REPLACE FUNCTION validate_order_shape() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    checked_order_id BIGINT;
    header customer_order%ROWTYPE;
    item_count INTEGER;
    invalid_item_count INTEGER;
BEGIN
    IF TG_TABLE_NAME = 'customer_order' THEN
        checked_order_id := NEW.id;
    ELSIF TG_OP = 'DELETE' THEN
        checked_order_id := OLD.order_id;
    ELSE
        checked_order_id := NEW.order_id;
    END IF;
    SELECT * INTO header FROM customer_order WHERE id = checked_order_id;
    IF NOT FOUND THEN RETURN NULL; END IF;

    SELECT count(*) INTO item_count FROM order_item WHERE order_id = checked_order_id;
    IF header.kind = 'DIRECT' THEN
        IF item_count < 1 THEN
            RAISE EXCEPTION 'direct order % must contain at least one item', checked_order_id;
        END IF;
        SELECT count(*) INTO invalid_item_count
          FROM order_item
         WHERE order_id = checked_order_id AND activity_discount_minor <> 0;
        IF invalid_item_count <> 0 THEN
            RAISE EXCEPTION 'direct order items cannot have an activity discount';
        END IF;
    ELSE
        IF item_count <> 1 THEN
            RAISE EXCEPTION 'activity order % must contain exactly one item', checked_order_id;
        END IF;
        SELECT count(*) INTO invalid_item_count
          FROM order_item oi
          JOIN marketing_activity ma ON ma.id = header.activity_id
         WHERE oi.order_id = checked_order_id AND oi.product_id <> ma.product_id;
        IF invalid_item_count <> 0 THEN
            RAISE EXCEPTION 'activity order item must be the configured activity product';
        END IF;
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER app_user_touch BEFORE UPDATE ON app_user FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER product_touch BEFORE UPDATE ON product FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER activity_touch BEFORE UPDATE ON marketing_activity FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER coupon_template_touch BEFORE UPDATE ON coupon_template FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER user_coupon_touch BEFORE UPDATE ON user_coupon FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER activity_inventory_checkpoint_touch BEFORE UPDATE ON activity_inventory_checkpoint FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER activity_inventory_sequence_touch BEFORE UPDATE ON activity_inventory_sequence FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER activity_recovery_job_touch BEFORE UPDATE ON activity_recovery_job FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER order_touch BEFORE UPDATE ON customer_order FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER reservation_touch BEFORE UPDATE ON inventory_reservation FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER coupon_reservation_touch BEFORE UPDATE ON coupon_reservation FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER payment_touch BEFORE UPDATE ON payment_record FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER product_operator_check BEFORE INSERT OR UPDATE ON product FOR EACH ROW EXECUTE FUNCTION enforce_operator_actor();
CREATE TRIGGER activity_operator_check BEFORE INSERT OR UPDATE ON marketing_activity FOR EACH ROW EXECUTE FUNCTION enforce_operator_actor();
CREATE TRIGGER coupon_template_operator_check BEFORE INSERT OR UPDATE ON coupon_template FOR EACH ROW EXECUTE FUNCTION enforce_operator_actor();
CREATE TRIGGER operator_audit_actor_check BEFORE INSERT ON operator_audit_log FOR EACH ROW EXECUTE FUNCTION enforce_audit_actor();
CREATE TRIGGER app_user_last_admin_update_check BEFORE UPDATE OF role, status ON app_user FOR EACH ROW EXECUTE FUNCTION prevent_last_admin_removal();
CREATE TRIGGER app_user_last_admin_delete_check BEFORE DELETE ON app_user FOR EACH ROW EXECUTE FUNCTION prevent_last_admin_removal();
CREATE TRIGGER order_outbox_touch BEFORE UPDATE ON order_outbox FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER coupon_outbox_touch BEFORE UPDATE ON coupon_outbox FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER inventory_outbox_touch BEFORE UPDATE ON inventory_outbox FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER payment_outbox_touch BEFORE UPDATE ON payment_outbox FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER product_outbox_touch BEFORE UPDATE ON product_outbox FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER activity_outbox_touch BEFORE UPDATE ON activity_outbox FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER order_message_idempotency_touch BEFORE UPDATE ON order_message_idempotency FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER coupon_message_idempotency_touch BEFORE UPDATE ON coupon_message_idempotency FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER inventory_message_idempotency_touch BEFORE UPDATE ON inventory_message_idempotency FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER payment_message_idempotency_touch BEFORE UPDATE ON payment_message_idempotency FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER order_transition_check BEFORE UPDATE OF status ON customer_order FOR EACH ROW EXECUTE FUNCTION enforce_order_transition();

-- Deferred checks make a header and all its order items insertable in one transaction.
CREATE CONSTRAINT TRIGGER order_header_shape_check
AFTER INSERT OR UPDATE OF kind, activity_id ON customer_order
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_order_shape();
CREATE CONSTRAINT TRIGGER order_item_shape_check
AFTER INSERT OR UPDATE OR DELETE ON order_item
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_order_shape();

-- Reservation operations must use conditional UPDATE statements in one transaction, e.g.:
-- UPDATE marketing_activity SET available_stock = available_stock - :qty, version = version + 1
-- WHERE id = :activity_id AND status = 'ACTIVE' AND starts_at <= now() AND now() < ends_at
--   AND available_stock >= :qty;
-- Direct orders use the equivalent product UPDATE with status = 'ON_SALE'.
