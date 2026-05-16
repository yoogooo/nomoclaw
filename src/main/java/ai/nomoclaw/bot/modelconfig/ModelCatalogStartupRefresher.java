package ai.nomoclaw.bot.modelconfig;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ModelCatalogStartupRefresher implements ApplicationRunner {

    private final ModelCatalogService modelCatalogService;

    public ModelCatalogStartupRefresher(ModelCatalogService modelCatalogService) {
        this.modelCatalogService = modelCatalogService;
    }

    @Override
    public void run(ApplicationArguments args) {
        modelCatalogService.refreshFromRemoteIfDueAsync();
    }
}
