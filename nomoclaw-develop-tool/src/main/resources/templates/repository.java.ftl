package ${package.ServiceImpl};

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import ${package.Entity}.${entity};
import ${package.Mapper}.${table.mapperName};

/**
 * ${table.comment!table.name} repository.
 *
 * @author ${author}
 * @since ${date}
 */
@Repository
public class ${table.serviceImplName} extends ServiceImpl<${table.mapperName}, ${entity}> {
}
