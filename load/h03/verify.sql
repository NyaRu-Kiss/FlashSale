-- H03 invariant queries. Run against the temporary PostgreSQL database after each
-- scenario has drained its Outbox and RocketMQ consumers.
SELECT 'negative_product_stock' AS check_name, count(*) AS violations
FROM product WHERE available_stock < 0;

SELECT 'negative_activity_stock' AS check_name, count(*) AS violations
FROM marketing_activity WHERE available_stock < 0;

SELECT 'duplicate_successful_orders' AS check_name, count(*) AS violations
FROM (
  SELECT user_id, idempotency_key, count(*) AS n
  FROM order_submission_idempotency
  WHERE status = 'SUCCEEDED' AND order_id IS NOT NULL
  GROUP BY user_id, idempotency_key HAVING count(*) > 1
) x;

SELECT 'activity_event_sequence_gaps' AS check_name, count(*) AS violations
FROM (
  SELECT activity_id, event_sequence,
         lag(event_sequence) OVER (PARTITION BY activity_id ORDER BY event_sequence) AS previous_sequence
  FROM activity_inventory_event
) x WHERE previous_sequence IS NOT NULL AND event_sequence <> previous_sequence + 1;

SELECT 'outbox_not_sent' AS check_name, count(*) AS pending
FROM (
  SELECT status FROM order_outbox UNION ALL SELECT status FROM coupon_outbox
  UNION ALL SELECT status FROM inventory_outbox UNION ALL SELECT status FROM payment_outbox
  UNION ALL SELECT status FROM product_outbox UNION ALL SELECT status FROM activity_outbox
) x WHERE status <> 'SENT';

SELECT 'consumer_not_succeeded' AS check_name, count(*) AS pending
FROM (
  SELECT status FROM order_message_idempotency UNION ALL SELECT status FROM coupon_message_idempotency
  UNION ALL SELECT status FROM inventory_message_idempotency UNION ALL SELECT status FROM payment_message_idempotency
) x WHERE status <> 'SUCCEEDED';

SELECT 'reservation_without_order' AS check_name, count(*) AS violations
FROM inventory_reservation r LEFT JOIN order_item i ON i.id = r.order_item_id
WHERE i.id IS NULL;
