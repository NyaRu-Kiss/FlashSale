-- FlashSale PostgreSQL 16 initial schema.
-- Monetary values are stored as integer minor units (for example, cents).

CREATE TYPE user_role AS ENUM ('CUSTOMER', 'OPERATOR');
-- user_role: CUSTOMER=普通用户；OPERATOR=运营人员。
COMMENT ON TYPE user_role IS '用户角色枚举。';
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
COMMENT ON TABLE app_user IS '平台用户与运营人员账户。';
COMMENT ON COLUMN app_user.id IS '用户主键。';
COMMENT ON COLUMN app_user.username IS '登录用户名，平台内唯一。';
COMMENT ON COLUMN app_user.password_hash IS '密码哈希值，不保存明文密码。';
COMMENT ON COLUMN app_user.role IS '用户角色，区分普通用户和运营人员。';
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
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_by BIGINT NOT NULL REFERENCES app_user(id),
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
COMMENT ON COLUMN marketing_activity.available_stock IS '活动库存池当前尚未被订单保留的数量。';
COMMENT ON COLUMN marketing_activity.purchase_limit_per_user IS '单个用户在该活动中的累计购买数量上限，包含待支付保留量。';
COMMENT ON COLUMN marketing_activity.starts_at IS '活动开始时间；以数据库服务器时间判断是否开始。';
COMMENT ON COLUMN marketing_activity.ends_at IS '活动结束时间；结束时刻不再接受新的活动订单。';
COMMENT ON COLUMN marketing_activity.status IS '活动生命周期状态。';
COMMENT ON COLUMN marketing_activity.version IS '活动配置/库存的乐观锁版本号。';
COMMENT ON COLUMN marketing_activity.created_by IS '创建该活动的运营人员。';
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
COMMENT ON COLUMN coupon_template.created_at IS '优惠券模板创建时间。';
COMMENT ON COLUMN coupon_template.updated_at IS '优惠券模板最后修改时间。';
CREATE INDEX coupon_template_claim_idx ON coupon_template (status, claim_starts_at, claim_ends_at);

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
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    status reservation_status NOT NULL DEFAULT 'RESERVED',
    reserved_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmed_at TIMESTAMPTZ,
    released_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((source = 'PRODUCT' AND product_id IS NOT NULL AND activity_id IS NULL) OR
           (source = 'ACTIVITY' AND activity_id IS NOT NULL AND product_id IS NULL))
);
COMMENT ON TABLE inventory_reservation IS '订单项对应的库存保留记录；每个订单项最多一条。';
COMMENT ON COLUMN inventory_reservation.id IS '库存保留记录主键。';
COMMENT ON COLUMN inventory_reservation.order_item_id IS '被该记录保留库存的订单项；一个订单项最多一条保留记录。';
COMMENT ON COLUMN inventory_reservation.source IS '库存池类型：商品库存或活动独立库存。';
COMMENT ON COLUMN inventory_reservation.product_id IS '商品库存池标识；source 为 PRODUCT 时填写。';
COMMENT ON COLUMN inventory_reservation.activity_id IS '活动库存池标识；source 为 ACTIVITY 时填写。';
COMMENT ON COLUMN inventory_reservation.quantity IS '本次订单项保留的库存数量。';
COMMENT ON COLUMN inventory_reservation.status IS '库存保留生命周期状态。';
COMMENT ON COLUMN inventory_reservation.reserved_at IS '库存成功保留时间。';
COMMENT ON COLUMN inventory_reservation.confirmed_at IS '支付成功后库存保留确认时间。';
COMMENT ON COLUMN inventory_reservation.released_at IS '订单取消或超时后库存释放时间。';
COMMENT ON COLUMN inventory_reservation.created_at IS '库存保留记录创建时间。';
COMMENT ON COLUMN inventory_reservation.updated_at IS '库存保留记录最后修改时间。';
CREATE INDEX inventory_reservation_active_idx ON inventory_reservation (source, activity_id, status) WHERE source = 'ACTIVITY';
CREATE INDEX inventory_reservation_product_idx ON inventory_reservation (source, product_id, status) WHERE source = 'PRODUCT';

CREATE TABLE inventory_movement (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id BIGINT REFERENCES inventory_reservation(id),
    source inventory_source NOT NULL,
    product_id BIGINT REFERENCES product(id),
    activity_id BIGINT REFERENCES marketing_activity(id),
    quantity_delta INTEGER NOT NULL CHECK (quantity_delta <> 0),
    reason inventory_movement_reason NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((source = 'PRODUCT' AND product_id IS NOT NULL AND activity_id IS NULL) OR
           (source = 'ACTIVITY' AND activity_id IS NOT NULL AND product_id IS NULL))
);
COMMENT ON TABLE inventory_movement IS '库存保留、释放、初始化和调整的不可变流水。';
COMMENT ON COLUMN inventory_movement.id IS '库存流水主键。';
COMMENT ON COLUMN inventory_movement.reservation_id IS '关联的库存保留记录；初始化或人工调整可为空。';
COMMENT ON COLUMN inventory_movement.source IS '发生变化的库存池类型。';
COMMENT ON COLUMN inventory_movement.product_id IS '商品库存标识；商品库存流水时填写。';
COMMENT ON COLUMN inventory_movement.activity_id IS '活动库存标识；活动库存流水时填写。';
COMMENT ON COLUMN inventory_movement.quantity_delta IS '库存变化量；负数表示扣减/保留，正数表示释放/增加。';
COMMENT ON COLUMN inventory_movement.reason IS '库存变化原因。';
COMMENT ON COLUMN inventory_movement.occurred_at IS '库存变化发生时间。';
CREATE INDEX inventory_movement_reservation_idx ON inventory_movement (reservation_id, occurred_at);

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

CREATE OR REPLACE FUNCTION touch_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = now();
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
CREATE TRIGGER order_touch BEFORE UPDATE ON customer_order FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER reservation_touch BEFORE UPDATE ON inventory_reservation FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER coupon_reservation_touch BEFORE UPDATE ON coupon_reservation FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER payment_touch BEFORE UPDATE ON payment_record FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
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
