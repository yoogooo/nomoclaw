# NomoClaw Develop Tool

基于 `MyBatis Plus Generator` 的数据库表结构生成工具，用于在建完表后直接生成固定风格的 `Entity / Mapper / Repository`，避免每次再手写模板代码。

## 用法

```bash
mvn -pl nomoclaw-develop-tool -q compile exec:java \
  -Dexec.args="--url=jdbc:mysql://127.0.0.1:3306/nomoclaw \
  --username=root \
  --password=secret \
  --package-name=ai.nomoclaw.bot.generated \
  --module-name=agent \
  --tables=agent_conversation,agent_message \
  --output-dir=./generated/code"
```

## 主要参数

- `--url`: JDBC 地址，必填
- `--username`: 数据库用户名，必填
- `--password`: 数据库密码，默认空
- `--package-name`: 生成代码的父包名，必填
- `--module-name`: 子模块包名，可选
- `--tables`: 逗号分隔的表名列表，必填
- `--output-dir`: 输出目录，默认 `generated/code`
- `--table-prefixes`: 要剥离的表前缀，默认 `t_,sys_`
- `--logic-delete-column`: 逻辑删除字段名，默认 `is_deleted`
- `--enable-swagger`: 是否生成 swagger 元数据，默认 `false`
- `--overwrite`: 是否覆盖已有文件，默认 `false`

## 生成结果

- `Entity`: 文件名后缀固定为 `Entity`
- `Mapper`: 使用 MyBatis Plus 默认 mapper 风格
- `Repository`: 基于模块内模板生成，文件名后缀固定为 `Repository`

## 说明

- 默认关闭 Mapper XML 生成
- 默认关闭 Controller 生成
- 默认不生成 Service 接口
- `TINYINT / SMALLINT` 会按 `Integer` 处理，避免旧表字段被映射成不符合预期的类型
