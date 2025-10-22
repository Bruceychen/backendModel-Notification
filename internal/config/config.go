package config

import (
	"fmt"
	"log"

	"github.com/spf13/viper"
)

// Config holds all application configuration
type Config struct {
	Server   ServerConfig   `mapstructure:"server"`
	Database DatabaseConfig `mapstructure:"database"`
	Redis    RedisConfig    `mapstructure:"redis"`
	RocketMQ RocketMQConfig `mapstructure:"rocketmq"`
	App      AppConfig      `mapstructure:"app"`
}

// ServerConfig holds HTTP server configuration
type ServerConfig struct {
	Port int `mapstructure:"port"`
}

// DatabaseConfig holds database connection configuration
type DatabaseConfig struct {
	Host     string `mapstructure:"host"`
	Port     int    `mapstructure:"port"`
	Username string `mapstructure:"username"`
	Password string `mapstructure:"password"`
	Database string `mapstructure:"database"`
}

// RedisConfig holds Redis connection configuration
type RedisConfig struct {
	Host string `mapstructure:"host"`
	Port int    `mapstructure:"port"`
	DB   int    `mapstructure:"db"`
}

// RocketMQConfig holds RocketMQ configuration
type RocketMQConfig struct {
	NameServers      []string `mapstructure:"name_servers"`
	ProducerGroup    string   `mapstructure:"producer_group"`
	ConsumerGroup    string   `mapstructure:"consumer_group"`
	SendTimeout      int      `mapstructure:"send_timeout"`
	RetryTimes       int      `mapstructure:"retry_times"`
}

// AppConfig holds application-specific configuration
type AppConfig struct {
	NotificationTopic       string `mapstructure:"notification_topic"`
	RecentListKey           string `mapstructure:"recent_list_key"`
	NotificationKeyPrefix   string `mapstructure:"notification_key_prefix"`
	CacheTTLMinutes         int    `mapstructure:"cache_ttl_minutes"`
}

// Load reads configuration from file and environment variables
func Load(configPath string) (*Config, error) {
	viper.SetConfigFile(configPath)
	viper.SetConfigType("yaml")
	viper.AutomaticEnv()

	if err := viper.ReadInConfig(); err != nil {
		return nil, fmt.Errorf("failed to read config file: %w", err)
	}

	var config Config
	if err := viper.Unmarshal(&config); err != nil {
		return nil, fmt.Errorf("failed to unmarshal config: %w", err)
	}

	log.Printf("Configuration loaded successfully from %s", configPath)
	return &config, nil
}

// GetDSN returns the MySQL DSN (Data Source Name)
func (c *DatabaseConfig) GetDSN() string {
	return fmt.Sprintf("%s:%s@tcp(%s:%d)/%s?charset=utf8mb4&parseTime=True&loc=Local",
		c.Username,
		c.Password,
		c.Host,
		c.Port,
		c.Database,
	)
}

// GetRedisAddr returns the Redis address
func (c *RedisConfig) GetRedisAddr() string {
	return fmt.Sprintf("%s:%d", c.Host, c.Port)
}