#!/bin/bash

# Update system
sudo yum update -y

# Install Docker
sudo yum install -y docker
sudo service docker start
sudo usermod -a -G docker ec2-user
sudo systemctl enable docker

# Initialize Docker Swarm
docker swarm init --advertise-addr $(curl -s http://169.254.169.254/latest/meta-data/local-ipv4)

# Install Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Create directory for application
mkdir -p ~/dms-app
cd ~/dms-app

# Install required packages
sudo yum install -y curl