package com.dir.sync.strategy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.dir.sync.model.DirOperation;
import com.dir.sync.strategy.impl.CreateDirStrategy;
import com.dir.sync.strategy.impl.DeleteDirStrategy;
import com.dir.sync.strategy.impl.UpdateDirStrategy;

@Component
public class BapiStrategyFactory {

    @Autowired
    private CreateDirStrategy createStrategy;

    @Autowired
    private UpdateDirStrategy updateStrategy;

    @Autowired
    private DeleteDirStrategy deleteStrategy;

    public BapiStrategy getStrategy(DirOperation operation) {
        return switch (operation) {
            case CREATE -> createStrategy;
            case UPDATE -> updateStrategy;
            case DELETE -> deleteStrategy;
        };
    }
}
