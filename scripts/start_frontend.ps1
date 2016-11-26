Push-Location $PSScriptRoot

.\start_rabbit.ps1

Pop-Location

docker inspect func-resty > $null 2> $null

if (! $?) {
       echo "Starting OpenResty"
       
       docker run -d -p 8680:80 --link func-rabbit --restart always --hostname func-resty --name func-resty funcatron/frontend:latest
       }

    
