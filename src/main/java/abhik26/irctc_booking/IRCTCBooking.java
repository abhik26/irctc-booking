package abhik26.irctc_booking;

import java.io.File;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;

public class IRCTCBooking {

	private static final String irctcUrl = "https://www.irctc.co.in/nget/train-search";

	private static final ZoneId indiaZoneId = ZoneId.of("Asia/Kolkata");
	private static final DateTimeFormatter journeyDateFormattter = DateTimeFormatter.ofPattern("dd/MM/uuuu")
			.withResolverStyle(ResolverStyle.STRICT);
	private static final DateTimeFormatter seatLinkDateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM")
			.withResolverStyle(ResolverStyle.STRICT);

	// Values are in seconds
	private static final int defaultImplicitWaitTime = 60;
	private static final int defaultExplicitWaitTime = 60;
	private static final int alternateImplicitWaitTime = 1;

	private static Properties bookingProperties = null;
	private static boolean tatkalWindow = false;
	private static String seatLinkDateSearch = null;
	private static boolean captchaTextExtractionEnabled;

	private static enum BookingProperty {
		USERNAME("irctc_username"), PASSWORD("irctc_password"), FROM_STATION("from_station_code"),
		TO_STATION("to_station_code"), JOURNEY_DATE("journey_date"), JOURNEY_QUOTA("journey_quota"),
		TRAIN_NUMBER("train_number"), TRAIN_CLASS("train_class"), PASSENGER_COUNT("passenger_count"),
		UPI_ID("upi_id"), CAPTCHA_TEXT_EXTRACTION_ENABLED("captcha_text_extraction_enabled");

		private final String name;

		BookingProperty(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return this.name;
		}
	}

	public static void main(String[] args) throws Exception {
		loadBookingProperties();
		validateBookingProperties();
		startBooking();
	}

	private static void loadBookingProperties() throws Exception {
		try (InputStream is = ClassLoader.getSystemClassLoader().getResourceAsStream("booking.properties")) {
			bookingProperties = new Properties();
			bookingProperties.load(is);
		} catch (Exception e) {
			throw e;
		}
	}

	private static void validateBookingProperties() {
		final String valueNotProvidedMessage = "Value not provided for property: ";
		final String invalidValueMessage = "Invalid value for property: ";
		final LocalDate indiaLocalDate = LocalDate.now(indiaZoneId);

		LocalDate journeyLocalDate = null;
		String journeyQuota = null;
		int passengerCountInt = 0;

		for (BookingProperty bookingProperty : BookingProperty.values()) {
			String propertyValue = bookingProperties.getProperty(bookingProperty.toString());

			if (propertyValue == null || propertyValue.trim().isEmpty()) {
				throw new RuntimeException(valueNotProvidedMessage + bookingProperty);
			}

			if (BookingProperty.JOURNEY_DATE.equals(bookingProperty)) {
				try {
					journeyLocalDate = LocalDate.parse(propertyValue.trim(), journeyDateFormattter);
				} catch (Exception e) {
					throw new RuntimeException(
							invalidValueMessage + bookingProperty + ". It should be in the proper format.");
				}

				if (journeyLocalDate.isBefore(indiaLocalDate)) {
					throw new RuntimeException(invalidValueMessage + bookingProperty
							+ ". It should not be in the past date.");
				}
			} else if (BookingProperty.JOURNEY_QUOTA.equals(bookingProperty)) {
				List<String> validJourneyQuotas = Arrays.asList("TATKAL", "GENERAL");

				if (validJourneyQuotas.indexOf(propertyValue.trim().toUpperCase()) < 0) {
					throw new RuntimeException(invalidValueMessage + bookingProperty);
				}

				LocalDate indiaNextDayLocalDate = indiaLocalDate.plus(1, ChronoUnit.DAYS);

				if ("TATKAL".equalsIgnoreCase(propertyValue.trim()) && (journeyLocalDate.isBefore(indiaNextDayLocalDate)
						|| journeyLocalDate.isAfter(indiaNextDayLocalDate))) {
					throw new RuntimeException(invalidValueMessage + bookingProperty);
				}

				journeyQuota = propertyValue.trim();
			} else if (BookingProperty.TRAIN_NUMBER.equals(bookingProperty)) {
				try {
					Integer.parseInt(propertyValue.trim());
				} catch (NumberFormatException e) {
					throw new RuntimeException(invalidValueMessage + bookingProperty);
				}
			} else if (BookingProperty.TRAIN_CLASS.equals(bookingProperty)) {
				List<String> validTrainClasses = Arrays.asList("2S", "SL", "CC", "3E", "3A", "2A", "1A");

				if (validTrainClasses.indexOf(propertyValue.trim().toUpperCase()) < 0) {
					throw new RuntimeException(invalidValueMessage + bookingProperty);
				}

				if ("TATKAL".equalsIgnoreCase(journeyQuota) && "1A".equalsIgnoreCase(propertyValue.trim())) {
					throw new RuntimeException("'1A' train class not applicable for 'TATKAL' journey quota.");
				}
			} else if (BookingProperty.PASSENGER_COUNT.equals(bookingProperty)) {
				try {
					passengerCountInt = Integer.parseInt(propertyValue.trim());
				} catch (NumberFormatException e) {
					throw new RuntimeException(invalidValueMessage + bookingProperty);
				}

				if (passengerCountInt < 1) {
					throw new RuntimeException(invalidValueMessage + bookingProperty);
				}

				if ("TATKAL".equalsIgnoreCase(journeyQuota) && passengerCountInt > 4) {
					throw new RuntimeException("Maximum 4 passengers are allowed in 'TATKAL' journey quota.");
				} else if (passengerCountInt > 6) {
					throw new RuntimeException("Maximum 6 passengers are allowed in 'GENERAL' journey quota.");
				}
			} else if (BookingProperty.UPI_ID.equals(bookingProperty)) {
				String upiIdRegex = "^(\\w+[.\\-])*\\w+@(\\w+[.\\-])*\\w+$";

				if (!propertyValue.trim().matches(upiIdRegex)) {
					throw new RuntimeException(invalidValueMessage + bookingProperty);
				}
			} else if (BookingProperty.CAPTCHA_TEXT_EXTRACTION_ENABLED.equals(bookingProperty)) {
				if (!propertyValue.trim().equalsIgnoreCase("true")
						&& !propertyValue.trim().equalsIgnoreCase("false")) {
					throw new RuntimeException(
							invalidValueMessage + bookingProperty);
				}

				captchaTextExtractionEnabled = Boolean.parseBoolean(propertyValue.trim());
			}
		}

		for (int i = 1; i <= passengerCountInt; i++) {
			String passengerKey = "passenger" + i;
			String passengerDetails = bookingProperties.getProperty(passengerKey);

			if (passengerDetails == null || passengerDetails.trim().isEmpty()) {
				throw new RuntimeException("Passenger details not provided for: " + passengerKey);
			}

			String[] detailsArray = passengerDetails.trim().split("\\s*\\|\\s*");

			if (detailsArray.length < 3) {
				throw new RuntimeException(
						"Mandatory fields (<full name> | <age> | <gender>) not provided for: " + passengerKey);
			}

			if (detailsArray[0].isEmpty()) {
				throw new RuntimeException("Passenger name not provided for: " + passengerKey);
			}

			if (detailsArray[1].isEmpty()) {
				throw new RuntimeException("Passenger age not provided for: " + passengerKey);
			} else {
				String invalidAgeMessage = "Invalid age, it should be between 1 and 125 for: " + passengerKey;

				try {
					int age = Integer.parseInt(detailsArray[1]);

					if (age < 1 || age > 125) {
						throw new RuntimeException(invalidAgeMessage);
					}
				} catch (NumberFormatException e) {
					throw new RuntimeException(invalidAgeMessage);
				}
			}

			if (detailsArray[2].isEmpty()) {
				throw new RuntimeException("Passenger gender not provided for: " + passengerKey);
			} else {
				List<String> validGenders = Arrays.asList("M", "F", "T");

				if (validGenders.indexOf(detailsArray[2].toUpperCase()) < 0) {
					throw new RuntimeException("Invalid gender for: " + passengerKey);

				}
			}

			if (detailsArray.length >= 4 && !detailsArray[3].isEmpty()) {
				List<String> validBerthPreferences = Arrays.asList("LB", "MB", "UB", "SL", "SU");

				if (validBerthPreferences.indexOf(detailsArray[3].toUpperCase()) < 0) {
					throw new RuntimeException("Invalid berth preference for: " + passengerKey);
				}
			}
		}

		seatLinkDateSearch = journeyLocalDate.format(seatLinkDateTimeFormatter);
	}

	@SuppressWarnings("unused")
	private static void startBooking() throws Exception {
		final WebDriver driver = DriverUtility.getDriver(BrowserName.CHROME);
		final WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(defaultExplicitWaitTime));
		final JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
		final Actions actions = new Actions(driver);

		driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(defaultImplicitWaitTime));

		boolean closeBrowser = true;

		LocalTime irctcTatkalWindowStart = LocalTime.of(9, 30).truncatedTo(ChronoUnit.MINUTES);
		LocalTime irctcTatkalWindowEnd = LocalTime.of(11, 31).truncatedTo(ChronoUnit.MINUTES);
		LocalTime indiaLocalTime = LocalTime.now(indiaZoneId);

		if (indiaLocalTime.isAfter(irctcTatkalWindowStart) && indiaLocalTime.isBefore(irctcTatkalWindowEnd)) {
			tatkalWindow = true;
		}

		try {
			driver.get(irctcUrl);

			// train search button
			WebElement trainSearchButton = driver.findElement(By.cssSelector(
					"button[type='submit'][class='search_btn train_Search'"));

			/*
			 * To be used during tatkal window i.e. between 09:30 AM to 11:30 AM.
			 */
			if (tatkalWindow) {
				/*
				 * Preventing login before threshold time for tatkal booking, i.e. 09:59 AM for
				 * AC and 10:59 AM for non AC classes.
				 */
				String journeyQuota = bookingProperties.getProperty(BookingProperty.JOURNEY_QUOTA.toString()).trim();

				if ("TATKAL".equalsIgnoreCase(journeyQuota)) {
					String trainClass = bookingProperties.getProperty(BookingProperty.TRAIN_CLASS.toString()).trim();

					LocalTime loginTimeThreshold = LocalTime.of(9, 59).truncatedTo(ChronoUnit.MINUTES);

					// Changing hour value to 10 for non AC classes
					if ("SL".equalsIgnoreCase(trainClass) || "2S".equalsIgnoreCase(trainClass)) {
						loginTimeThreshold = loginTimeThreshold.withHour(10);
					}

					indiaLocalTime = LocalTime.now(indiaZoneId);

					if (indiaLocalTime.isBefore(loginTimeThreshold)) {
						throw new RuntimeException("Trying to login before: " + loginTimeThreshold);
					}
				}

				// click train search button
				trainSearchButton.click();
				signIn(driver, wait);
			}

			// From station
			WebElement fromStationInput = driver.findElement(By.cssSelector("input[aria-controls='pr_id_1_list']"));
			fromStationInput.sendKeys(bookingProperties.getProperty(BookingProperty.FROM_STATION.toString()).trim());
			WebElement fromStationOption = driver.findElement(By.cssSelector("#pr_id_1_list li:first-child"));
			wait.until(ExpectedConditions.elementToBeClickable(fromStationOption));
			fromStationOption.click();

			// To station
			WebElement toStationInput = driver.findElement(By.cssSelector("input[aria-controls='pr_id_2_list']"));
			toStationInput.sendKeys(bookingProperties.getProperty(BookingProperty.TO_STATION.toString()).trim());
			WebElement toStationOption = driver.findElement(By.cssSelector("#pr_id_2_list li:first-child"));
			wait.until(ExpectedConditions.elementToBeClickable(toStationOption));
			toStationOption.click();

			// Journey date selection
			WebElement datePickerInput = driver
					.findElement(By.cssSelector("span[class='ng-tns-c58-10 ui-calendar'] input"));
			datePickerInput.sendKeys(Keys.CONTROL, "a", Keys.BACK_SPACE);
			datePickerInput.sendKeys(bookingProperties.getProperty(BookingProperty.JOURNEY_DATE.toString()).trim());

			// Journey Quota dropdown
			WebElement journeyQuotaDropdown = driver.findElement(By.id("journeyQuota"));
			actions.click(journeyQuotaDropdown).perform();

			// jouney quota option selection
			WebElement journeyQuotaOption = driver
					.findElement(By.cssSelector("div[class='ui-dropdown-items-wrapper ng-tns-c65-12']"))
					.findElement(By.cssSelector(String.format("li[aria-label='%s']",
							bookingProperties.getProperty(BookingProperty.JOURNEY_QUOTA.toString()).trim()
									.toUpperCase())));
			actions.click(journeyQuotaOption).perform();

			// click train search button
			trainSearchButton.click();

			WebElement train = driver.findElement(By.xpath(String.format(
					"//strong[contains(text(), '(%s)')]/ancestor::div[contains(@class, 'border-all')]",
					bookingProperties.getProperty(BookingProperty.TRAIN_NUMBER.toString()).trim())));

			// waiting for ad to load to prevent unnecessary error
			if (!tatkalWindow) {
				TimeUnit.SECONDS.sleep(2);
			}

			// scroll to the train (if needed)
			actions.moveToElement(train).perform();

			/*
			 * Sleeping the thread until the tatkal booking start time is reached for the
			 * specified train class
			 */
			if (tatkalWindow) {
				String journeyQuota = bookingProperties.getProperty(BookingProperty.JOURNEY_QUOTA.toString()).trim();

				if ("TATKAL".equalsIgnoreCase(journeyQuota)) {
					String trainClass = bookingProperties.getProperty(BookingProperty.TRAIN_CLASS.toString()).trim();

					// tatkal booking start time with default value as 10:00 AM for AC classes
					LocalTime tatkalBookingStartTime = LocalTime.of(10, 0, 1).truncatedTo(ChronoUnit.SECONDS);

					// Changing hour value to 11 for non AC classes
					if ("SL".equalsIgnoreCase(trainClass) || "2S".equalsIgnoreCase(trainClass)) {
						tatkalBookingStartTime = tatkalBookingStartTime.withHour(11);
					}

					indiaLocalTime = LocalTime.now(indiaZoneId);
					long timeDifferenceInMillis = Duration
							.between(indiaLocalTime, tatkalBookingStartTime).toMillis();

					if (timeDifferenceInMillis > 0) {
						// Time remaining for the booking to start should be less than 1 minute (60000
						// milliseconds).
						if (timeDifferenceInMillis > 60000) {
							throw new RuntimeException("More than one minute is remaining for the booking to start.");
						}

						TimeUnit.MILLISECONDS.sleep(timeDifferenceInMillis);
					}
				}
			}

			// click train class
			WebElement trainClassLink = train.findElement(
					By.xpath(String.format(".//td//*[contains(text(), '(%s)')]/ancestor::td",
							bookingProperties.getProperty(BookingProperty.TRAIN_CLASS.toString())
									.trim().toUpperCase())));
			actions.click(trainClassLink).perform();

			// click first available date (specified date)
			WebElement seatAvailableLink = train.findElement(By.xpath(
					String.format(".//td//strong[contains(text(), '%s')]/ancestor::td", seatLinkDateSearch)));

			// scroll to train again (if needed)
			actions.moveToElement(train).perform();

			if (seatAvailableLink.findElements(By.cssSelector("div[class*='AVAILABLE']")).size() == 0) {
				String message = "Seat not available for the given class in the provided date.";
				jsExecutor.executeScript(String.format("window.alert('%s')", message));
				TimeUnit.SECONDS.sleep(alternateImplicitWaitTime);
				throw new RuntimeException(message);
			}

			// adding sleep time to maintain consistency in selecting available seat
			actions.pause(Duration.ofMillis(200)).click(seatAvailableLink).perform();

			// click book now
			WebElement bookTrainButton = train.findElement(By.xpath(".//button[contains(text(), 'Book Now')]"));
			actions.click(bookTrainButton).perform();

			/*
			 * To be used when exact stations are not mentioned.
			 */
			try {
				driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(alternateImplicitWaitTime));
				WebElement confirmButton = driver.findElement(
						By.xpath("//span[@class='ui-button-text ui-clickable'][contains(text(), 'Yes')]"));
				confirmButton.click();
			} catch (Exception e) {
				// e.printStackTrace();
			} finally {
				driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(defaultImplicitWaitTime));
			}

			/*
			 * To be used outside the tatkal time window.
			 */
			if (!tatkalWindow) {
				signIn(driver, wait);
			}

			int passengerCount = Integer
					.parseInt(bookingProperties.getProperty(BookingProperty.PASSENGER_COUNT.toString()).trim());

			if (passengerCount > 0) {
				for (int i = 1; i <= passengerCount; i++) {
					String[] passengerDetails = bookingProperties.getProperty("passenger" + i).trim()
							.split("\\s*\\|\\s*");

					// add passenger details
					List<WebElement> appPassengers = driver.findElements(By.tagName("app-passenger"));
					WebElement appPassenger = appPassengers.get(i - 1);

					actions.moveToElement(appPassenger).perform();

					WebElement passengerNameInput = appPassenger
							.findElement(By.cssSelector("input[placeholder='Passenger Name']"));

					// limiting to maximum characters allowed in the passenger name field
					String passengerNameMaxLength = passengerNameInput.getDomAttribute("maxLength");
					if (passengerNameMaxLength != null) {
						int maxPassengerNameLength = Integer.parseInt(passengerNameMaxLength);

						if (passengerDetails[0].length() > maxPassengerNameLength) {
							passengerDetails[0] = passengerDetails[0].substring(0, maxPassengerNameLength);
						}
					}

					// fill passenger name
					passengerNameInput.sendKeys(passengerDetails[0]);

					// fill passenger age
					WebElement passengerAgeInput = appPassenger.findElement(By.cssSelector("input[placeholder='Age']"));
					passengerAgeInput.click();
					passengerAgeInput.sendKeys(passengerDetails[1]);

					// select passenger gender
					WebElement passengerGenderDropdown = appPassenger
							.findElement(By.cssSelector("select[formcontrolname='passengerGender']"));
					passengerGenderDropdown.click();
					passengerGenderDropdown
							.findElement(By.cssSelector(
									String.format("option[value='%s']", passengerDetails[2].toUpperCase())))
							.click();

					// select passenger berth preference
					if (passengerDetails.length >= 4) {
						WebElement berthChoiceDropdown = appPassenger
								.findElement(By.cssSelector("select[formcontrolname='passengerBerthChoice']"));
						berthChoiceDropdown.click();
						berthChoiceDropdown
								.findElement(By.cssSelector(
										String.format("option[value='%s']", passengerDetails[3].toUpperCase())))
								.click();
					}

					// click add passenger link
					if (i < passengerCount) {
						WebElement addPassengerLink = driver
								.findElement(By.xpath("//span[contains(text(), 'Add Passenger')]/parent::a"));
						wait.until(ExpectedConditions.elementToBeClickable(addPassengerLink));
						actions.click(addPassengerLink).perform();
					}
				}

				// select 'Consider for Auto Upgradation.' checkbox
				WebElement autoUpgradationCheckbox = driver.findElement(By.cssSelector("[for='autoUpgradation']"));
				actions.click(autoUpgradationCheckbox).perform();

				// select 'Book only if confirm berths are allotted.' checkbox
				WebElement confirmBirthCheckbox = driver.findElement(By.cssSelector("[for='confirmberths']"));
				actions.click(confirmBirthCheckbox).perform();

				// select 'pay through bhim/upi' radio button
				WebElement paymentTypeRadio = driver
						.findElement(By.cssSelector("p-radiobutton[name='paymentType'][id='2'] div[role='radio']"));
				actions.click(paymentTypeRadio).perform();

				// click continue button
				WebElement continueButton = driver.findElement(
						By.xpath("//button[@class='train_Search btnDefault'][contains(text(), 'Continue')]"));
				actions.click(continueButton).perform();

				/*
				 * selecting 'No' for 'Passengers may get berth allotted in different coaches'
				 * dialog box.
				 */
				try {
					driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(alternateImplicitWaitTime));
					WebElement confirmButton = driver.findElement(
							By.xpath("//span[@class='ui-button-text ui-clickable'][contains(text(), 'No')]"));
					confirmButton.click();
				} catch (Exception e) {
					// e.printStackTrace();
				} finally {
					driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(defaultImplicitWaitTime));
				}

				// increasing wait time to review the journey
				driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(defaultImplicitWaitTime * 2));

				// Prevent browser exit in case of any error from here
				closeBrowser = false;

				// final captcha
				WebElement finalCaptcha = driver.findElement(By.id("captcha"));

				// continue button for clicking after entering final captcha
				WebElement continueButtonOnReview = driver.findElement(By.xpath(
						"//button[@class='btnDefault train_Search'][contains(text(), 'Continue')]"));

				// process to extract and fill captcha image and click continue button
				if (captchaTextExtractionEnabled) {
					extractAndFillCaptchaImageText(driver, continueButtonOnReview);
				}

				WebElement irctcIPayOption = driver
						.findElement(By.xpath("//span[contains(text(), 'IRCTC iPay')]/parent::div"));

				// click on irctc ipay option if not selected
				if (!irctcIPayOption.getDomAttribute("class").contains("bank-type-active")) {
					wait.until(ExpectedConditions.elementToBeClickable(irctcIPayOption));
					irctcIPayOption.click();
				}

				// click on pay and book
				WebElement payAndBookButton = driver.findElement(
						By.xpath("//button[contains(text(), 'Pay & Book')][contains(@class, 'btn btn-primary')]"));
				wait.until(ExpectedConditions.elementToBeClickable(payAndBookButton));
				// payAndBookButton.click();
				jsExecutor.executeScript("arguments[0].click()", payAndBookButton);

				// fill upi id
				driver.findElement(By.id("vpaCheck"))
						.sendKeys(bookingProperties.getProperty(BookingProperty.UPI_ID.toString()).trim());

				// click pay
				WebElement finalPayButton = driver.findElement(By.id("upi-sbmt"));
				wait.until(ExpectedConditions.elementToBeClickable(finalPayButton));
				finalPayButton.click();
			}
		} finally {
			if (closeBrowser) {
				driver.quit();
			}
		}
	}

	private static void signIn(WebDriver driver, WebDriverWait wait) {
		// username input
		WebElement userIdInput = driver.findElement(By.cssSelector("input[formcontrolname='userid']"));
		userIdInput.sendKeys(bookingProperties.getProperty(BookingProperty.USERNAME.toString()).trim());

		// password input
		WebElement passwordInput = driver.findElement(By.cssSelector("input[formcontrolname='password']"));
		wait.until(ExpectedConditions.elementToBeClickable(passwordInput));
		new Actions(driver).click(passwordInput).perform();
		passwordInput.sendKeys(bookingProperties.getProperty(BookingProperty.PASSWORD.toString()).trim());

		// sign in button
		WebElement signInButton = driver.findElement(By.xpath("//button[@type='submit'][contains(text(), 'SIGN IN')]"));

		if (captchaTextExtractionEnabled) {
			extractAndFillCaptchaImageText(driver, signInButton);
		}

		wait.until(ExpectedConditions.invisibilityOf(signInButton));

		handlePreviousPendingTransactionPopup(driver);
	}

	private static void handlePreviousPendingTransactionPopup(WebDriver driver) {
		try {
			driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(alternateImplicitWaitTime));
			WebElement closeTransactionButton = driver.findElement(By.xpath(
					"//div[@aria-labelledby='ui-dialog-2-label'] //button[contains(text(), 'Close')]"));
			closeTransactionButton.click();
		} catch (Exception e) {
			// e.printStackTrace();
		} finally {
			driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(defaultImplicitWaitTime));
		}
	}

	public static void extractAndFillCaptchaImageText(WebDriver driver, WebElement continueButton) {
		try {
			ITesseract tesseract = new Tesseract();
			File traineddataFile = new File(
					ClassLoader.getSystemClassLoader().getResource("eng.traineddata").getPath());
			tesseract.setDatapath(traineddataFile.getParent());
			String captchaImageExtractedText = "";

			WebElement captchaImageElement = driver.findElement(By.cssSelector("img.captcha-img"));
			WebElement captchaInputElement = driver.findElement(By.cssSelector("input#captcha"));

			// waiting for ad to load to prevent scroll issue
			if (!tatkalWindow) {
				TimeUnit.SECONDS.sleep(1);
			}

			/*
			 * scrolling captcha image element in the middle of the page
			 * for consistently taking screenshot for image text extraction.
			 */
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'})",
					captchaImageElement);

			File captchaImageFile = captchaImageElement.getScreenshotAs(OutputType.FILE);
			System.out.println("\n" + captchaImageFile);

			captchaImageExtractedText = tesseract.doOCR(captchaImageFile).replaceAll("\\s*", "");
			System.out.println("\n" + captchaImageExtractedText);

			// filling captcha image text in the input field
			captchaInputElement.sendKeys(captchaImageExtractedText);
			new Actions(driver).click(continueButton).perform();
		} catch (Exception e) {
			// e.printStackTrace();
		}
	}

}
