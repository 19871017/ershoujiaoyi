package com.secondhand.platform.modules.address.application;

import com.secondhand.platform.modules.address.AddressRequest;
import com.secondhand.platform.modules.address.AddressResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AddressApplicationServiceTest {
    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private AddressApplicationService service;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        service = new AddressApplicationService(jdbcTemplate);
    }

    @Test
    void saveAddressShouldPersistServerOwnedAddressAndDefaultState() {
        long userId = 8101L;
        AddressRequest first = address("雨哥", "13800138101", "广东省 深圳市 南山区", "科技园 1 号", true);
        AddressRequest second = address("小原", "13800138102", "广东省 深圳市 福田区", "中心区 2 号", true);

        AddressResponse savedFirst = service.saveAddress(userId, first);
        AddressResponse savedSecond = service.saveAddress(userId, second);
        List<AddressResponse> rows = service.listAddresses(userId);

        assertEquals(savedSecond.addressId(), rows.get(0).addressId());
        assertEquals(true, savedSecond.isDefault());
        assertEquals(false, service.getAddress(userId, savedFirst.addressId()).isDefault());
        assertEquals("小原", rows.get(0).name());
        assertEquals("138****8102", rows.get(0).mobile());
        assertEquals(2, rows.size());
    }

    @Test
    void updateSetDefaultAndDeleteShouldBeOwnerScoped() {
        AddressResponse ownerAddress = service.saveAddress(8201L, address("雨哥", "13800138201", "上海市 浦东新区", "世纪大道 8 号", false));
        AddressResponse otherAddress = service.saveAddress(8202L, address("别人", "13800138202", "上海市 黄浦区", "外滩 9 号", true));
        AddressRequest update = address("雨哥新地址", "13800138203", "上海市 静安区", "南京西路 10 号", true);
        update.setAddressId(ownerAddress.addressId());

        AddressResponse updated = service.saveAddress(8201L, update);
        service.setDefault(8201L, updated.addressId());
        service.deleteAddress(8201L, updated.addressId());

        assertEquals("雨哥新地址", updated.name());
        assertEquals(true, updated.isDefault());
        assertEquals(0, service.listAddresses(8201L).size());
        assertEquals(1, service.listAddresses(8202L).size());
        assertEquals(otherAddress.addressId(), service.listAddresses(8202L).get(0).addressId());
        assertThrows(IllegalArgumentException.class, () -> service.getAddress(8201L, otherAddress.addressId()));
    }

    @Test
    void saveAddressShouldValidateInputAndRejectCrossOwnerUpdate() {
        AddressResponse otherAddress = service.saveAddress(8302L, address("别人", "13800138302", "浙江省 杭州市 西湖区", "文三路 3 号", true));
        AddressRequest invalid = address(" ", "123", "", "", false);
        AddressRequest crossOwner = address("雨哥", "13800138301", "浙江省 杭州市 上城区", "湖滨 1 号", false);
        crossOwner.setAddressId(otherAddress.addressId());

        assertThrows(IllegalArgumentException.class, () -> service.saveAddress(8301L, invalid));
        assertThrows(IllegalArgumentException.class, () -> service.saveAddress(8301L, crossOwner));
        assertThrows(IllegalArgumentException.class, () -> service.listAddresses(0L));
        assertThrows(IllegalArgumentException.class, () -> service.setDefault(8301L, otherAddress.addressId()));
        assertEquals(1, service.listAddresses(8302L).size());
    }

    private AddressRequest address(String name, String mobile, String provinceCity, String detail, boolean isDefault) {
        AddressRequest request = new AddressRequest();
        request.setName(name);
        request.setMobile(mobile);
        request.setProvinceCity(provinceCity);
        request.setDetail(detail);
        request.setIsDefault(isDefault);
        return request;
    }
}
