package com.icepark.util;

import com.icepark.enums.AgeGroup;

/**
 * 游客年龄段与器材目标年龄段的匹配规则。
 *
 * 器材的目标年龄段是一个确定分组（幼童/青少年/成人），游客手持自己的年龄段领装，
 * 规则为"同组匹配"：游客年龄段必须与该器材在本场次中配置的目标年龄段一致。
 * 目标年龄段可在场次内被调整（见客群调整模块），发装时以绑定关系上的最新值为准。
 * 以后若要支持"青少年器材兼容幼童"等区间规则，只需扩展本方法。
 */
public final class AgeGroupMatcher {

    private AgeGroupMatcher() {
    }

    public static boolean isAllowed(AgeGroup visitorAgeGroup, AgeGroup targetAgeGroup) {
        return visitorAgeGroup == targetAgeGroup;
    }
}
