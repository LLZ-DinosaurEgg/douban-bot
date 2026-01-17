package com.douban.bot.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@ConfigurationPropertiesBinding
public class ListConfig implements Converter<String, List<String>> {

    @Override
    public List<String> convert(String source) {
        if (source == null || source.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(source.split(","));
    }
}
