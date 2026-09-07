package com.example.zuoaiagent.raglab.entity;

/**
 * 灰度发布计划展示对象（包含关联的模型名称）
 */
public class GrayReleasePlanVO extends GrayReleasePlanDO {
    private String modelNames;

    public String getModelNames() { return modelNames; }
    public void setModelNames(String modelNames) { this.modelNames = modelNames; }
}
