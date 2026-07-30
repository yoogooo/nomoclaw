package ai.nomoclaw.bot.knowledge;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;

import javax.sql.DataSource;
import java.lang.reflect.Field;

/**
 * Creates MyBatis-Plus repositories for focused H2 tests without a Spring context.
 */
public final class TestRepositorySupport {

    private final SqlSessionTemplate sqlSessionTemplate;

    public TestRepositorySupport(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        this.sqlSessionTemplate = new SqlSessionTemplate(factoryBean.getObject());
    }

    public <M> M mapper(Class<M> mapperType) {
        if (!sqlSessionTemplate.getConfiguration().hasMapper(mapperType)) {
            sqlSessionTemplate.getConfiguration().addMapper(mapperType);
        }
        return sqlSessionTemplate.getMapper(mapperType);
    }

    public <M extends BaseMapper<T>, T, R extends CrudRepository<M, T>> R repository(
            Class<R> repositoryType, Class<M> mapperType) throws Exception {
        R repository = repositoryType.getDeclaredConstructor().newInstance();
        Field baseMapper = CrudRepository.class.getDeclaredField("baseMapper");
        baseMapper.setAccessible(true);
        baseMapper.set(repository, mapper(mapperType));
        return repository;
    }
}
