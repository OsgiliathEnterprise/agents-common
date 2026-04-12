package net.example.module.cucumber;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@CucumberContextConfiguration
@SpringBootTest(classes = net.example.module.ModuleApplication.class)
public class CucumberSpringConfiguration {
}
