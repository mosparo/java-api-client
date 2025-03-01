package io.mosparo.client;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;

@Testcontainers
@ExtendWith(org.testcontainers.junit.jupiter.TestcontainersExtension.class)
abstract class AbstractMosparoIT {

    public static final String MOSPARO_WEB_COMPOSE_SERVICE = "mosparo_web";
    public static final int MOSPARO_WEB_EXPOSED_PORT = 80;

    static ComposeContainer environment;

    static String mosparoUrl;
    static String projectUuid;
    static String projectPublicKey;
    static String projectPrivateKey;

    static RemoteWebDriver driver;

    @BeforeAll
    static void startEnvironment() throws IOException {
        environment = new ComposeContainer(new File("src/test/resources/docker-compose.yaml"))
                .withExposedService(MOSPARO_WEB_COMPOSE_SERVICE, MOSPARO_WEB_EXPOSED_PORT)
                .withExposedService("firefox", 4444);
        environment.start();
        environment.waitingFor(MOSPARO_WEB_COMPOSE_SERVICE, Wait.defaultWaitStrategy());

        String mosparoHost = environment.getServiceHost(MOSPARO_WEB_COMPOSE_SERVICE, MOSPARO_WEB_EXPOSED_PORT);
        Integer mosparoPort = environment.getServicePort(MOSPARO_WEB_COMPOSE_SERVICE, MOSPARO_WEB_EXPOSED_PORT);
        mosparoUrl = "http://" + mosparoHost + ":" + mosparoPort;

        // Connecting to selenium container
        String firefoxHost = environment.getServiceHost("firefox", 4444);
        Integer firefoxPort = environment.getServicePort("firefox", 4444);
        URL spec = new URL("http://" + firefoxHost + ":" + firefoxPort + "/wd/hub");
        FirefoxOptions options = new FirefoxOptions();
        driver = new RemoteWebDriver(spec, options);
        driver.setLogLevel(Level.ALL);

        // Page: Database
        driver.get("http://mosparo_web/setup/database");
        new WebDriverWait(driver, Duration.ofSeconds(60)).until(ExpectedConditions.titleIs("Database - mosparo"));
        new Select(driver.findElement(By.id("form_system"))).selectByVisibleText("MySQL/MariaDB");
        driver.findElement(By.id("form_host")).sendKeys("db");
        driver.findElement(By.id("form_port")).clear();
        driver.findElement(By.id("form_port")).sendKeys("3306");
        driver.findElement(By.id("form_database")).sendKeys("mosparo");
        driver.findElement(By.id("form_user")).sendKeys("mosparo");
        driver.findElement(By.id("form_password")).sendKeys("password");
        scrollIntoViewAndClick(By.cssSelector("button[type='submit']"));

        // Page: Other information
        new WebDriverWait(driver, Duration.ofSeconds(5)).until(ExpectedConditions.titleIs("Other information - mosparo"));
        driver.findElement(By.id("form_name")).sendKeys("mosparo");
        driver.findElement(By.id("form_emailAddress")).sendKeys("test@mosparo.io");
        driver.findElement(By.id("form_password_plainPassword_first")).sendKeys("password");
        driver.findElement(By.id("form_password_plainPassword_second")).sendKeys("password");
        scrollIntoViewAndClick(By.cssSelector("button[type='submit']"));

        // The installation was successfully completed
        new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(ExpectedConditions.elementToBeClickable(By.linkText("Go to login")));
        scrollIntoViewAndClick(By.linkText("Go to login"));

        // Page: Login
        new WebDriverWait(driver, Duration.ofSeconds(5)).until(ExpectedConditions.titleIs("Login - mosparo"));
        driver.findElement(By.id("field-email")).sendKeys("test@mosparo.io");
        driver.findElement(By.id("field-password")).sendKeys("password");
        scrollIntoViewAndClick(By.cssSelector("button[type='submit']"));

        // Page: Projects
        new WebDriverWait(driver, Duration.ofSeconds(5)).until(ExpectedConditions.titleIs("Projects - mosparo"));
        scrollIntoViewAndClick(By.xpath("//button[normalize-space()='Create']"));
        scrollIntoViewAndClick(By.linkText("Create project"));

        // Page: Create project
        new WebDriverWait(driver, Duration.ofSeconds(5)).until(ExpectedConditions.titleIs("Create project - mosparo"));
        driver.findElement(By.id("project_form_name")).sendKeys("tests");
        driver.findElement(By.id("project_form_description"))
                .sendKeys("Test project for mosparo Java API MosparoDefaultClient");
        driver.findElement(By.cssSelector("input[id^='project_form_hosts_']")).sendKeys("*");
        scrollIntoViewAndClick(By.cssSelector("button[type='submit']"));

        // Page: Create project › Select design
        new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(ExpectedConditions.titleIs("Create project › Select design - mosparo"));
        scrollIntoViewAndClick(By.cssSelector("button[type='submit']"));

        // Page: Create project › Enable security features
        new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(ExpectedConditions.titleIs("Create project › Enable security features - mosparo"));
        scrollIntoViewAndClick(By.cssSelector("button[type='submit']"));

        // Page: Create project › Connection details
        new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(ExpectedConditions.titleIs("Create project › Connection details - mosparo"));
        projectUuid = driver.findElement(By.id("projectUuid")).getDomAttribute("value");
        projectPublicKey = driver.findElement(By.id("projectPublicKey")).getDomAttribute("value");
        projectPrivateKey = driver.findElement(By.id("projectPrivateKey")).getDomAttribute("value");

        // Generate the demo form
        String html = IOUtils.resourceToString("index.html", StandardCharsets.UTF_8,
                AbstractMosparoIT.class.getClassLoader());
        html = html.replace("#projectUuid#", projectUuid);
        html = html.replace("#projectPublicKey#", projectPublicKey);
        ContainerState website = environment.getContainerByServiceName("website").orElseThrow();
        website.copyFileToContainer(Transferable.of(html), "/usr/share/nginx/html/index.html");
    }

    static void scrollIntoViewAndClick(By locator) {
        WebElement element = driver.findElement(locator);
        driver.executeScript("arguments[0].scrollIntoView({ behavior: \"instant\"});", element);
        element.click();
    }

    Map<String, Object> visitForm(Map<String, Object> input) {
        driver.get("http://website/index.html");
        new WebDriverWait(driver, Duration.ofSeconds(5)).until(ExpectedConditions.titleIs("Test"));

        Map<String, Object> formData = new HashMap<>();

        // Fill the form
        input.forEach((key, value) -> {
            driver.findElement(By.id(key)).sendKeys((String) value);
            formData.put(key, value);
        });

        // Click mosparo checkbox by moving the mouse pointer
        WebElement element = driver.findElement(By.cssSelector("input[id^='_mosparo_checkboxField_']"));
        new Actions(driver).moveToElement(element).click().perform();

        // Retrieve the tokens
        WebElement submitTokenElement = driver.findElement(By.name("_mosparo_submitToken"));
        new WebDriverWait(driver, Duration.ofSeconds(2)).until(
                ExpectedConditions.not(ExpectedConditions.domAttributeToBe(submitTokenElement, "value", "")));

        String mosparoSubmitToken = submitTokenElement.getDomAttribute("value");
        formData.put("_mosparo_submitToken", mosparoSubmitToken);

        WebElement validationTokenElement = driver.findElement(By.name("_mosparo_validationToken"));
        new WebDriverWait(driver, Duration.ofSeconds(2)).until(
                ExpectedConditions.not(ExpectedConditions.domAttributeToBe(validationTokenElement, "value", "")));

        String mosparoValidationToken = validationTokenElement.getDomAttribute("value");
        formData.put("_mosparo_validationToken", mosparoValidationToken);

        // Submit form
        scrollIntoViewAndClick(By.cssSelector("button[type='submit']"));

        return formData;
    }
}
