package com.dtrps.modules.itinerary.repository;

import com.dtrps.modules.itinerary.entity.WeatherForecast;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeatherForecastRepository extends JpaRepository<WeatherForecast, Long> {}
