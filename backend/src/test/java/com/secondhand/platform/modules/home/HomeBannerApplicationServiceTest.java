package com.secondhand.platform.modules.home;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class HomeBannerApplicationServiceTest {
    private HomeBannerApplicationService service;

    @BeforeEach
    void setUp() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        service = new HomeBannerApplicationService(new JdbcTemplate(database));
    }

    @Test
    void merchantShowcaseListsOnlyFirstThreeEnabledBannersBySortOrder() {
        service.adminCreate(request("商家秀一", 40, true));
        service.adminCreate(request("商家秀停用", 45, false));
        service.adminCreate(request("商家秀二", 50, true));
        service.adminCreate(request("商家秀三", 60, true));
        service.adminCreate(request("商家秀四", 70, true));

        List<HomeBannerResponse> banners = service.listMerchantShowcaseEnabled();

        assertEquals(3, banners.size());
        assertEquals("商家秀一", banners.get(0).title());
        assertEquals("商家秀二", banners.get(1).title());
        assertEquals("商家秀三", banners.get(2).title());
        assertEquals("MERCHANT_SHOWCASE", banners.get(0).placement());
    }

    @Test
    void sortOrderRemainsGloballyUniqueForExistingDatabaseCompatibility() {
        AdminHomeBannerRequest homeBanner = request("首页轮播", 40, true);
        homeBanner.setPlacement("HOME");
        service.adminCreate(homeBanner);

        AdminHomeBannerRequest duplicateMerchantBanner = request("商家秀重复排序", 40, true);

        assertThrows(IllegalArgumentException.class, () -> service.adminCreate(duplicateMerchantBanner));
    }

    private AdminHomeBannerRequest request(String title, int sortOrder, boolean enabled) {
        AdminHomeBannerRequest request = new AdminHomeBannerRequest();
        request.setKicker("小原圈商家秀");
        request.setTitle(title);
        request.setDescription("顶部轮播图由后台配置");
        request.setCta("查看");
        request.setImageUrl("/uploads/home/merchant-" + sortOrder + ".jpg");
        request.setAction("none");
        request.setPlacement("MERCHANT_SHOWCASE");
        request.setSortOrder(sortOrder);
        request.setEnabled(enabled);
        return request;
    }
}
