# 部署说明

## 环境要求

- JDK 25+（本机已按 JDK25 + Lombok 1.18.46 验证编译；也可用 JDK17 将 pom 中 java.version 改回 17）
- Maven 3.9+
- Node.js 18+
- Docker Desktop（推荐）或本地 MySQL 8 / Redis 7

## Docker 依赖

```bash
cd D:\smart-campus-canteen\docker
docker compose up -d
```

默认：

- MySQL: `localhost:3306` / root / `123456`（本机已按此密码配置；Docker 同）
- Redis: `localhost:6379`

若不用 Docker，请手工执行：

1. `sql/01_schema.sql`
2. `sql/02_seed.sql`

并修改 `backend/src/main/resources/application.yml` 中的数据源密码。

## 后端部署

```bash
cd D:\smart-campus-canteen\backend
mvn clean package -DskipTests
java -jar target/smart-canteen-1.0.0.jar
```

生产建议：

- 修改 `canteen.jwt.secret`
- 关闭 MyBatis SQL 打印
- 配置 Nginx 反代 `/api` → `8080`

## 前端部署

```bash
cd D:\smart-campus-canteen\frontend
npm install
npm run build
```

将 `dist/` 交给 Nginx：

```nginx
server {
  listen 80;
  server_name canteen.campus.local;
  root /var/www/canteen;
  index index.html;

  location /api/ {
    proxy_pass http://127.0.0.1:8080/api/;
  }

  location / {
    try_files $uri $uri/ /index.html;
  }
}
```

## 验收 Checklist

- [ ] 学生登录成功，个人中心手机号脱敏
- [ ] 标签组合搜索（如 LOW_FAT + HALAL）返回正确
- [ ] 下单后库存减少，余额扣减
- [ ] 管理员档口看板可流转订单状态
- [ ] 营养周报图表渲染
- [ ] 数据看板折线/环形图有数据
- [ ] Excel 日结可导出
