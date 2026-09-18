-- KEYS[1] = stock key
-- ARGV[1] = quantity to deduct
-- return remaining stock (>=0) or -1 if insufficient
local stock = tonumber(redis.call('GET', KEYS[1]) or '-1')
if stock < 0 then
  return -2
end
local qty = tonumber(ARGV[1])
if stock < qty then
  return -1
end
return redis.call('DECRBY', KEYS[1], qty)
