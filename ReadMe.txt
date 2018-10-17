sudo ./gatling.sh -s PerformanceTest -rf "results" 0 1 100 1


0 - scenario (0 - Calculate and place, 1 - calculate, 2 - place)
1 - request ratio (this to 1) example 1:1
100 - concurrent users
1 - duration (in minutes)