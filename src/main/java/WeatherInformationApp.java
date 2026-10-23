import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class WeatherInformationApp extends JFrame {
    // Replace this value with your OpenWeatherMap API key.
    private static final String API_KEY = "Your OpenWeatherMap API key";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final JTextField cityField = new JTextField("Yangon", 16);
    private final JComboBox<String> temperatureUnit =
            new JComboBox<>(new String[]{"Celsius (°C)", "Fahrenheit (°F)"});
    private final JComboBox<String> windUnit =
            new JComboBox<>(new String[]{"m/s", "km/h", "mph"});
    private final JLabel cityLabel = new JLabel("Search for a city");
    private final JLabel iconLabel = new JLabel("🌤️", SwingConstants.CENTER);
    private final JLabel temperatureLabel = new JLabel("-- °C");
    private final JLabel conditionLabel = new JLabel("Weather details will appear here.");
    private final JLabel humidityLabel = new JLabel("Humidity: --");
    private final JLabel windLabel = new JLabel("Wind: --");
    private final JLabel updatedLabel = new JLabel(" ");
    private final JTextArea forecastArea = new JTextArea(6, 24);
    private final DefaultListModel<String> historyModel = new DefaultListModel<>();
    private final JList<String> historyList = new JList<>(historyModel);
    private final JPanel mainPanel = new JPanel(new BorderLayout(12, 12));

    private double temperatureC;
    private double windMetersPerSecond;
    private boolean hasWeather = false;
    private JSONArray latestForecast;

    public WeatherInformationApp() {
        super("Weather Information App");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(720, 560);
        setLocationRelativeTo(null);

        mainPanel.setBorder(new EmptyBorder(18, 18, 18, 18));
        setContentPane(mainPanel);

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        searchPanel.add(new JLabel("City:"));
        searchPanel.add(cityField);
        JButton searchButton = new JButton("Get Weather");
        searchPanel.add(searchButton);
        searchPanel.add(new JLabel("Temperature:"));
        searchPanel.add(temperatureUnit);
        searchPanel.add(new JLabel("Wind:"));
        searchPanel.add(windUnit);
        mainPanel.add(searchPanel, BorderLayout.NORTH);

        JPanel weatherPanel = new JPanel(new BorderLayout(8, 8));
        weatherPanel.setOpaque(false);
        cityLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        temperatureLabel.setFont(new Font("SansSerif", Font.BOLD, 34));
        iconLabel.setFont(new Font("SansSerif", Font.PLAIN, 54));
        cityLabel.setForeground(new Color(24, 48, 78));
        temperatureLabel.setForeground(new Color(18, 100, 150));
        conditionLabel.setForeground(new Color(55, 72, 91));
        humidityLabel.setForeground(new Color(55, 72, 91));
        windLabel.setForeground(new Color(55, 72, 91));
        updatedLabel.setForeground(new Color(95, 108, 122));

        JPanel currentPanel = new JPanel(new BorderLayout(10, 4));
        currentPanel.setOpaque(true);
        currentPanel.setBackground(Color.WHITE);
        currentPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(205, 218, 230)),
                new EmptyBorder(14, 16, 14, 16)));
        currentPanel.add(iconLabel, BorderLayout.WEST);

        JPanel detailsPanel = new JPanel();
        detailsPanel.setOpaque(false);
        detailsPanel.setLayout(new BoxLayout(detailsPanel, BoxLayout.Y_AXIS));
        detailsPanel.add(cityLabel);
        detailsPanel.add(temperatureLabel);
        detailsPanel.add(conditionLabel);
        detailsPanel.add(Box.createVerticalStrut(8));
        detailsPanel.add(humidityLabel);
        detailsPanel.add(windLabel);
        detailsPanel.add(updatedLabel);
        currentPanel.add(detailsPanel, BorderLayout.CENTER);

        forecastArea.setEditable(false);
        forecastArea.setBackground(Color.WHITE);
        forecastArea.setForeground(new Color(35, 51, 68));
        forecastArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        forecastArea.setLineWrap(false);
        JScrollPane forecastScroll = new JScrollPane(forecastArea);
        forecastScroll.setBorder(BorderFactory.createTitledBorder("Short-Term Forecast"));

        JPanel centerPanel = new JPanel(new BorderLayout(10, 12));
        centerPanel.setOpaque(false);
        centerPanel.add(currentPanel, BorderLayout.NORTH);
        centerPanel.add(forecastScroll, BorderLayout.CENTER);
        weatherPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(weatherPanel, BorderLayout.CENTER);

        JPanel historyPanel = new JPanel(new BorderLayout(8, 8));
        historyPanel.setPreferredSize(new Dimension(190, 0));
        historyPanel.setBackground(Color.WHITE);
        historyPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(205, 218, 230)),
                new EmptyBorder(10, 10, 10, 10)));
        JLabel historyTitle = new JLabel("Recent Searches");
        historyTitle.setFont(new Font("SansSerif", Font.BOLD, 14));
        historyTitle.setForeground(new Color(24, 48, 78));
        historyList.setBackground(Color.WHITE);
        historyList.setForeground(new Color(35, 51, 68));
        historyPanel.add(historyTitle, BorderLayout.NORTH);
        historyPanel.add(new JScrollPane(historyList), BorderLayout.CENTER);
        mainPanel.add(historyPanel, BorderLayout.EAST);

        searchButton.addActionListener(e -> searchWeather());
        cityField.addActionListener(e -> searchWeather());
        temperatureUnit.addActionListener(e -> updateDisplayedUnits());
        windUnit.addActionListener(e -> updateDisplayedUnits());

        updateBackground();
    }

    private void searchWeather() {
        String city = cityField.getText().trim();
        if (city.isEmpty()) {
            showError("Please enter a city name.");
            return;
        }
        if (API_KEY.equals("YOUR_OPENWEATHERMAP_API_KEY")) {
            showError("Add your OpenWeatherMap API key to the API_KEY constant in the source code.");
            return;
        }

        setBusy(true);
        new SwingWorker<WeatherResult, Void>() {
            @Override
            protected WeatherResult doInBackground() throws Exception {
                String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
                JSONObject current = getJson(
                        "https://api.openweathermap.org/data/2.5/weather?q="
                                + encodedCity + "&appid=" + API_KEY + "&units=metric");
                JSONObject forecast = getJson(
                        "https://api.openweathermap.org/data/2.5/forecast?q="
                                + encodedCity + "&appid=" + API_KEY + "&units=metric");
                return new WeatherResult(current, forecast);
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    WeatherResult result = get();
                    displayWeather(result.current, result.forecast);
                    addHistory(city);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause();
                    String message = cause == null ? ex.getMessage() : cause.getMessage();
                    showError("Could not get weather data. Check the city, internet connection, and API key.\n"
                            + (message == null ? "" : message));
                }
            }
        }.execute();
    }

    private JSONObject getJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        JSONObject json = new JSONObject(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalArgumentException(json.optString("message", "Weather API request failed."));
        }
        return json;
    }

    private void displayWeather(JSONObject current, JSONObject forecast) {
        JSONObject main = current.getJSONObject("main");
        JSONObject weather = current.getJSONArray("weather").getJSONObject(0);
        JSONObject wind = current.getJSONObject("wind");

        temperatureC = main.getDouble("temp");
        windMetersPerSecond = wind.optDouble("speed", 0);
        hasWeather = true;

        cityLabel.setText(current.optString("name", cityField.getText()));
        conditionLabel.setText(capitalize(weather.optString("description", "Unknown")));
        humidityLabel.setText("Humidity: " + main.optInt("humidity") + "%");
        updatedLabel.setText("Updated: " + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        iconLabel.setText(weatherEmoji(weather.optString("main", "")));

        updateDisplayedUnits();
        latestForecast = forecast.getJSONArray("list");
        displayForecast(latestForecast);
        updateBackground();
    }

    private void displayForecast(JSONArray entries) {
        StringBuilder text = new StringBuilder();
        text.append(String.format("%-18s %-12s %-10s%n", "Date / Time", "Temp", "Condition"));
        text.append("--------------------------------------------------\n");
        int count = Math.min(8, entries.length()); // Eight 3-hour periods, about 24 hours.
        for (int i = 0; i < count; i++) {
            JSONObject item = entries.getJSONObject(i);
            String date = item.optString("dt_txt", "");
            double tempC = item.getJSONObject("main").getDouble("temp");
            String condition = item.getJSONArray("weather").getJSONObject(0)
                    .optString("description", "Unknown");
            double displayedTemp = "Fahrenheit (°F)".equals(temperatureUnit.getSelectedItem())
                    ? tempC * 9 / 5 + 32 : tempC;
            String symbol = "Fahrenheit (°F)".equals(temperatureUnit.getSelectedItem()) ? "°F" : "°C";
            text.append(String.format("%-18s %-12s %-10s%n",
                    date.length() >= 16 ? date.substring(5, 16) : date,
                    String.format("%.1f%s", displayedTemp, symbol), condition));
        }
        forecastArea.setText(text.toString());
    }

    private void updateDisplayedUnits() {
        if (!hasWeather) return;
        boolean fahrenheit = "Fahrenheit (°F)".equals(temperatureUnit.getSelectedItem());
        double displayedTemp = fahrenheit ? temperatureC * 9 / 5 + 32 : temperatureC;
        temperatureLabel.setText(String.format("%.1f °%s", displayedTemp, fahrenheit ? "F" : "C"));

        String unit = (String) windUnit.getSelectedItem();
        double displayedWind = windMetersPerSecond;
        if ("km/h".equals(unit)) displayedWind *= 3.6;
        if ("mph".equals(unit)) displayedWind *= 2.23694;
        windLabel.setText(String.format("Wind: %.1f %s", displayedWind, unit));

        if (latestForecast != null) {
            displayForecast(latestForecast);
        }
    }

    private void addHistory(String city) {
        String entry = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                + " — " + city;
        historyModel.add(0, entry);
        while (historyModel.size() > 10) historyModel.remove(historyModel.size() - 1);
    }

    private void updateBackground() {
        int hour = LocalDateTime.now().getHour();
        if (hour >= 6 && hour < 11) {
            mainPanel.setBackground(new Color(255, 235, 190)); // Morning
        } else if (hour >= 11 && hour < 17) {
            mainPanel.setBackground(new Color(205, 232, 250)); // Daytime
        } else if (hour >= 17 && hour < 20) {
            mainPanel.setBackground(new Color(250, 205, 170)); // Evening
        } else {
            mainPanel.setBackground(new Color(45, 60, 90)); // Night
            mainPanel.setForeground(Color.WHITE);
        }
        mainPanel.repaint();
    }

    private String weatherEmoji(String condition) {
        return switch (condition.toLowerCase()) {
            case "clear" -> "☀️";
            case "clouds" -> "☁️";
            case "rain", "drizzle" -> "🌧️";
            case "thunderstorm" -> "⛈️";
            case "snow" -> "❄️";
            case "mist", "smoke", "haze", "fog" -> "🌫️";
            default -> "🌤️";
        };
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) return "Unknown";
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    private void setBusy(boolean busy) {
        setCursor(Cursor.getPredefinedCursor(busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Weather Information App",
                JOptionPane.ERROR_MESSAGE);
    }

    private record WeatherResult(JSONObject current, JSONObject forecast) {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new WeatherInformationApp().setVisible(true));
    }
}
