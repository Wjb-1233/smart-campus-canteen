# API Smoke Test
$base = 'http://localhost:8080/api'
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body '{"studentNo":"2021001001","password":"123456"}'
if ($login.code -ne 0) { throw "login failed" }
$h = @{ Authorization = "Bearer $($login.data.token)" }
$profile = Invoke-RestMethod -Uri "$base/auth/profile" -Headers $h
$search = Invoke-RestMethod -Uri "$base/dish/search?mealPeriod=LUNCH" -Headers $h
$order = Invoke-RestMethod -Method Post -Uri "$base/order/place" -Headers $h -ContentType 'application/json' -Body '{"mealPeriod":"LUNCH","payChannel":"BALANCE","items":[{"dishId":2,"quantity":1}]}'
$dash = Invoke-RestMethod -Uri "$base/dashboard/overview" -Headers $h
Write-Host "OK profile=$($profile.data.realName) dishes=$($search.data.Count) order=$($order.data.orderNo) pending=$($dash.data.pendingOrders)"
