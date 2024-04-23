package com.example.abhik26.irctc_booking;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;

public interface DriverUtility {

	public static WebDriver getDriver(BrowserName browserName) {
		WebDriver driver = null;
		
		if (browserName == null) {
			throw new NullPointerException("browser name is not provided");
		}
		
		try {
			switch (browserName) {
				case CHROME:
					System.setProperty("webdriver.chrome.driver",
							ClassLoader.getSystemResource("localdata/chromedriver.exe").getPath());
					ChromeOptions options = new ChromeOptions();
					options.addArguments("--remote-allow-origins=*");
					options.addArguments("--start-maximized");
					options.addArguments("--disable-notifications");
					// options.addArguments("--user-data-dir=C:\\Users\\" + System.getProperty("user.name")
					// 		+ "\\AppData\\Local\\Google\\Chrome\\User Data");
					driver = new ChromeDriver(options);
					break;
				case EDGE:
					System.setProperty("webdriver.edge.driver",
							ClassLoader.getSystemResource("localdata/msedgedriver.exe").getPath());
					driver = new EdgeDriver();
					break;
				case FIREFOX:
					System.setProperty("webdriver.gecko.driver",
							ClassLoader.getSystemResource("localdata/geckodriver.exe").getPath());
	                driver = new FirefoxDriver();
					break;
				default:
					System.setProperty("webdriver.chrome.driver",
							ClassLoader.getSystemResource("localdata/chromedriver.exe").getPath());
					driver = new ChromeDriver();
					break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return driver;
	}
}
