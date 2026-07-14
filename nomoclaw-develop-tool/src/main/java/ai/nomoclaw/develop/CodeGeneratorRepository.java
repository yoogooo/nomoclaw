package ai.nomoclaw.develop;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.type.JdbcType;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.rules.DateType;
import com.baomidou.mybatisplus.generator.config.rules.DbColumnType;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;
import com.baomidou.mybatisplus.generator.fill.Column;
import com.baomidou.mybatisplus.generator.function.ConverterFileName;

/**
 * Command-line MyBatis Plus code generator for table-driven scaffolding.
 */
public final class CodeGeneratorRepository {

    private static final String DEFAULT_OUTPUT_DIR = "generated/code";
    private static final String DEFAULT_LOGIC_DELETE_COLUMN = "is_deleted";
    private static final List<String> DEFAULT_TABLE_PREFIXES = List.of("t_", "sys_");

    private CodeGeneratorRepository() {
    }

    /**
     * Generates entity, mapper and repository classes from database tables.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        if (args.length == 0 || containsHelp(args)) {
            printUsage(System.out);
            return;
        }
        try {
            GeneratorOptions options = GeneratorOptions.parse(args);
            run(options);
        } catch (IllegalArgumentException exception) {
            System.err.println("Code generation failed: " + exception.getMessage());
            System.err.println();
            printUsage(System.err);
            System.exit(1);
        } catch (Exception exception) {
            throw new RuntimeException("Code generation failed", exception);
        }
    }

    private static void run(GeneratorOptions options) throws Exception {
        Path outputPath = Path.of(options.outputDir()).toAbsolutePath().normalize();
        Files.createDirectories(outputPath);

        FastAutoGenerator.create(options.url(), options.username(), options.password())
                .dataSourceConfig(builder -> builder.typeConvertHandler((globalConfig, typeRegistry, metaInfo) -> {
                    if (JdbcType.TINYINT == metaInfo.getJdbcType() || JdbcType.SMALLINT == metaInfo.getJdbcType()) {
                        return DbColumnType.INTEGER;
                    }
                    return typeRegistry.getColumnType(metaInfo);
                }))
                .globalConfig(builder -> {
                    builder.author(options.author())
                            .outputDir(outputPath.toString())
                            .dateType(DateType.TIME_PACK);
                    if (options.enableSwagger()) {
                        builder.enableSwagger();
                    }
                })
                .packageConfig(builder -> {
                    builder.parent(options.packageName())
                            .serviceImpl("repository");
                    if (!options.moduleName().isBlank()) {
                        builder.moduleName(options.moduleName());
                    }
                })
                .strategyConfig(builder -> {
                    builder.addInclude(options.tables());
                    if (!options.tablePrefixes().isEmpty()) {
                        builder.addTablePrefix(options.tablePrefixes().toArray(String[]::new));
                    }

                    builder.entityBuilder()
                            .convertFileName(entityName -> entityName + "Entity")
                            .enableLombok()
                            .enableTableFieldAnnotation()
                            .addTableFills(new Column("created_time", FieldFill.INSERT))
                            .addTableFills(new Column("updated_time", FieldFill.INSERT_UPDATE))
                            .logicDeleteColumnName(options.logicDeleteColumn());

                    builder.mapperBuilder()
                            .enableMapperAnnotation()
                            .disableMapperXml();

                    builder.serviceBuilder()
                            .disableService()
                            .serviceImplTemplate("/templates/repository.java")
                            .convertServiceImplFileName(repositoryFileName());

                    builder.controllerBuilder().disable();

                    if (options.overwrite()) {
                        builder.entityBuilder().enableFileOverride();
                        builder.mapperBuilder().enableFileOverride();
                        builder.serviceBuilder().enableFileOverride();
                    }
                })
                .templateEngine(new FreemarkerTemplateEngine())
                .execute();
    }

    private static ConverterFileName repositoryFileName() {
        return entityName -> entityName + "Repository";
    }

    private static boolean containsHelp(String[] args) {
        return Arrays.stream(args).anyMatch(argument -> "--help".equals(argument) || "-h".equals(argument));
    }

    private static void printUsage(PrintStream output) {
        output.println("Usage:");
        output.println("  mvn -pl nomoclaw-develop-tool -q compile exec:java \\");
        output.println("    -Dexec.args=\"--url=jdbc:mysql://127.0.0.1:3306/nomoclaw \\");
        output.println("    --username=root --password=secret \\");
        output.println("    --package-name=ai.nomoclaw.bot.generated \\");
        output.println("    --module-name=agent \\");
        output.println("    --tables=agent_conversation,agent_message \\");
        output.println("    --output-dir=./generated/code\"");
        output.println();
        output.println("Required options:");
        output.println("  --url=...             JDBC URL");
        output.println("  --username=...        Database username");
        output.println("  --package-name=...    Parent package for generated code");
        output.println("  --tables=...          Comma-separated table list");
        output.println();
        output.println("Optional options:");
        output.println("  --password=...                Database password, default empty");
        output.println("  --module-name=...             Module package segment, default empty");
        output.println("  --output-dir=...              Output directory, default " + DEFAULT_OUTPUT_DIR);
        output.println("  --author=...                  Author tag, default current OS user");
        output.println("  --table-prefixes=t_,sys_      Comma-separated stripped prefixes");
        output.println("  --logic-delete-column=...     Logic delete column, default " + DEFAULT_LOGIC_DELETE_COLUMN);
        output.println("  --enable-swagger=true|false   Whether to generate swagger metadata, default false");
        output.println("  --overwrite=true|false        Whether to overwrite existing files, default false");
    }

    /**
     * Parsed generator options.
     *
     * @param url JDBC URL
     * @param username database username
     * @param password database password
     * @param packageName parent package for generated code
     * @param moduleName module package segment
     * @param outputDir output directory
     * @param author generated code author tag
     * @param tables database tables to generate
     * @param tablePrefixes table prefixes to strip
     * @param logicDeleteColumn logic delete column
     * @param enableSwagger whether to generate swagger metadata
     * @param overwrite whether to overwrite existing files
     */
    private record GeneratorOptions(
            String url,
            String username,
            String password,
            String packageName,
            String moduleName,
            String outputDir,
            String author,
            List<String> tables,
            List<String> tablePrefixes,
            String logicDeleteColumn,
            boolean enableSwagger,
            boolean overwrite) {

        /**
         * Parses CLI arguments into validated generator options.
         *
         * @param args command-line arguments
         * @return parsed options
         */
        private static GeneratorOptions parse(String[] args) {
            Map<String, String> values = new LinkedHashMap<>();
            for (String argument : args) {
                if (!argument.startsWith("--") || !argument.contains("=")) {
                    throw new IllegalArgumentException("Invalid argument: " + argument);
                }
                int separatorIndex = argument.indexOf('=');
                String key = argument.substring(2, separatorIndex).trim();
                String value = argument.substring(separatorIndex + 1).trim();
                if (key.isBlank()) {
                    throw new IllegalArgumentException("Argument key must not be blank");
                }
                values.put(key, value);
            }

            String url = required(values, "url");
            String username = required(values, "username");
            String packageName = required(values, "package-name");
            List<String> tables = splitCsv(required(values, "tables"));
            if (tables.isEmpty()) {
                throw new IllegalArgumentException("At least one table is required");
            }

            return new GeneratorOptions(
                    url,
                    username,
                    values.getOrDefault("password", ""),
                    packageName,
                    values.getOrDefault("module-name", ""),
                    values.getOrDefault("output-dir", DEFAULT_OUTPUT_DIR),
                    values.getOrDefault("author", System.getProperty("user.name", "")),
                    tables,
                    values.containsKey("table-prefixes")
                            ? splitCsv(values.get("table-prefixes"))
                            : DEFAULT_TABLE_PREFIXES,
                    values.getOrDefault("logic-delete-column", DEFAULT_LOGIC_DELETE_COLUMN),
                    Boolean.parseBoolean(values.getOrDefault("enable-swagger", "false")),
                    Boolean.parseBoolean(values.getOrDefault("overwrite", "false")));
        }

        private static String required(Map<String, String> values, String key) {
            String value = values.get(key);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing required option --" + key);
            }
            return value;
        }

        private static List<String> splitCsv(String raw) {
            List<String> results = new ArrayList<>();
            for (String part : raw.split(",")) {
                String normalized = part.trim();
                if (!normalized.isBlank()) {
                    results.add(normalized);
                }
            }
            return results;
        }
    }
}
