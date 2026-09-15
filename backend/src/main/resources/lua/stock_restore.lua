-- KEYS[1] = stock key
-- ARGV[1] = quantity to restore
local qty = tonumber(ARGV[1])
if qty == nil or qty <= 0 then
  return -1
end
if redis.call('EXISTS', KEYS[1]) == 0 then
  redis.call('SET', KEYS[1], qty)
  return qty
end
return redis.call('INCRBY', KEYS[1], qty)
