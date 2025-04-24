#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>

#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define OLED_RESET -1

Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);

const int voltagePin = A0;
const int redLed = 7;
const int greenLed = 8;
const float thresholdVoltage = 4.0;
float voltage = 0;

void setup() {
Serial.begin(9600);
pinMode(redLed,OUTPUT);
pinMode(greenLed,OUTPUT);

if(!display.begin(SSD1306_SWITCHCAPVCC, 0x3C)){
  Serial.println(F("Eroare OLED"));
  for(;;);
}

display.clearDisplay();
display.setTextSize(2);
display.setTextColor(SSD1306_WHITE);
display.setCursor(0, 0);
display.println("Initializare... ");
display.display();
delay(1000);
}

void loop() {
int raw = analogRead(voltagePin);
voltage = (raw / 1023.0) * 5.0;

Serial.println("Tensiunea de pe pin:");
Serial.println(voltage);

if(voltage > thresholdVoltage){
  digitalWrite(redLed, HIGH);
  digitalWrite(greenLed, LOW);
}
else{
  digitalWrite(redLed, LOW);
  digitalWrite(greenLed, HIGH);
}

display.clearDisplay();
display.setCursor(0, 10);
display.setTextSize(1);
display.print("Tensiune: ");
display.setTextSize(2);
display.setCursor(0, 30);
display.print(voltage, 2);
display.print("V");
display.display();

delay(300);
}
