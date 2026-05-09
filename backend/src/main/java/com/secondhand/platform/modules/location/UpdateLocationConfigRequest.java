package com.secondhand.platform.modules.location;

public class UpdateLocationConfigRequest {
    private String provider;
    private Boolean enabled;
    private String defaultCity;
    private String defaultProvince;
    private String coordinateType;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getDefaultCity() { return defaultCity; }
    public void setDefaultCity(String defaultCity) { this.defaultCity = defaultCity; }

    public String getDefaultProvince() { return defaultProvince; }
    public void setDefaultProvince(String defaultProvince) { this.defaultProvince = defaultProvince; }

    public String getCoordinateType() { return coordinateType; }
    public void setCoordinateType(String coordinateType) { this.coordinateType = coordinateType; }
}
