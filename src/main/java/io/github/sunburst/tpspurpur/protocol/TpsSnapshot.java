package io.github.sunburst.tpspurpur.protocol;

public record TpsSnapshot(double mspt,
                          double tps,
                          long sprintTicks,
                          boolean frozen,
                          boolean sprinting,
                          boolean stepping) {
}
