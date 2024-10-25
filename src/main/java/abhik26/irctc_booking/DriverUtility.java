package abhik26.irctc_booking;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;

public interface DriverUtility {

	public static WebDriver getDriver(BrowserName browserName) {
		WebDriver driver = null;
		
		if (browserName == null) {
			throw new NullPointerException("browser name is not provided");
		}

		final String PATH_PREFIX = "localdata/";
		
		switch (browserName) {
			case CHROME:
				System.setProperty("webdriver.chrome.driver",
						ClassLoader.getSystemResource(PATH_PREFIX + "chromedriver.exe").getPath());
				ChromeOptions chromeOptions = new ChromeOptions();
				chromeOptions.addArguments("--start-maximized");
				chromeOptions.addArguments("--disable-notifications");
				// options.addArguments("--user-data-dir=C:\\Users\\" + System.getProperty("user.name")
				// 		+ "\\AppData\\Local\\Google\\Chrome\\User Data");
				driver = new ChromeDriver(chromeOptions);
				break;
			case EDGE:
				System.setProperty("webdriver.edge.driver",
						ClassLoader.getSystemResource(PATH_PREFIX + "msedgedriver.exe").getPath());
				EdgeOptions edgeOptions = new EdgeOptions();
				edgeOptions.addArguments("--start-maximized");
				edgeOptions.addArguments("--disable-notifications");
				driver = new EdgeDriver(edgeOptions);
				break;
		}
		
		return driver;
	}
}
