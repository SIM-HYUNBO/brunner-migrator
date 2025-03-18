package com.brunner.db.migration;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ConfigLoader {
    private final JsonNode config;

    // 설정 파일을 로드하는 생성자
    public ConfigLoader(String filePath) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        config = objectMapper.readTree(new File(filePath));
    }

    // 설정 파일의 데이터를 반환
    public JsonNode getConfig() {
        return config;
    }
}