/* Copyright (C) 2026 SFG545. Licensed under the Numa-Only License (OOL-1.0), see LICENSE-OOL. */

#include <iostream>
#include <string>

#include "planner/pair_scorer.h"

int main() {
  std::string line;
  while (std::getline(std::cin, line)) {
    try {
      const auto request = numa::planner::Json::parse(line);
      if (request.contains("analyses")) {
        std::cout << numa::planner::SortQueue(
            request["analyses"], request.value("initial", numa::planner::Json(nullptr))) << '\n';
      } else {
        std::cout << numa::planner::ScorePair(
            request.at("analysis"), request.at("nextAnalysis")) << '\n';
      }
    } catch (const std::exception& error) {
      std::cerr << error.what() << '\n';
      return 1;
    }
  }
}
