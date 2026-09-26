-- H03-only XXL-Job bootstrap. The business services register the same app_name
-- and expose outboxDispatch; these rows make the schedule reproducible in a
-- fresh H03 xxl-mysql volume.
INSERT INTO xxl_job_group (app_name, name, title, address_type, update_time)
VALUES
  ('flashsale-product', 'flashsale-product', 'FlashSale Product', 0, NOW()),
  ('flashsale-coupon', 'flashsale-coupon', 'FlashSale Coupon', 0, NOW()),
  ('flashsale-activity', 'flashsale-activity', 'FlashSale Activity', 0, NOW()),
  ('flashsale-inventory', 'flashsale-inventory', 'FlashSale Inventory', 0, NOW()),
  ('flashsale-order', 'flashsale-order', 'FlashSale Order', 0, NOW()),
  ('flashsale-payment', 'flashsale-payment', 'FlashSale Payment', 0, NOW())
ON DUPLICATE KEY UPDATE title = VALUES(title), update_time = VALUES(update_time);

INSERT INTO xxl_job_info (
  job_group, name, job_desc, add_time, update_time, author, alarm_email,
  schedule_type, schedule_conf, misfire_strategy, executor_route_strategy,
  executor_handler, executor_param, executor_block_strategy, executor_timeout,
  executor_fail_retry_count, executor_fail_strategy, glue_type, glue_source,
  glue_remark, glue_updatetime, child_jobid, trigger_status
)
SELECT g.id, g.app_name, CONCAT('H03 outbox dispatch - ', g.app_name), NOW(), NOW(),
       'flashsale-h03', '', 'CRON', '0/1 * * * * ?', 'DO_NOTHING', 'FIRST',
       'outboxDispatch', '', 'SERIAL_EXECUTION', 30, 1, 'FAILOVER', 'BEAN',
       '', 'H03 bootstrap', NOW(), '', 1
FROM xxl_job_group g
WHERE g.app_name IN (
  'flashsale-product', 'flashsale-coupon', 'flashsale-activity',
  'flashsale-inventory', 'flashsale-order', 'flashsale-payment'
)
ON DUPLICATE KEY UPDATE
  schedule_type = VALUES(schedule_type),
  schedule_conf = VALUES(schedule_conf),
  misfire_strategy = VALUES(misfire_strategy),
  executor_route_strategy = VALUES(executor_route_strategy),
  executor_handler = VALUES(executor_handler),
  executor_param = VALUES(executor_param),
  executor_block_strategy = VALUES(executor_block_strategy),
  executor_timeout = VALUES(executor_timeout),
  executor_fail_retry_count = VALUES(executor_fail_retry_count),
  executor_fail_strategy = VALUES(executor_fail_strategy),
  trigger_status = VALUES(trigger_status),
  update_time = VALUES(update_time);
