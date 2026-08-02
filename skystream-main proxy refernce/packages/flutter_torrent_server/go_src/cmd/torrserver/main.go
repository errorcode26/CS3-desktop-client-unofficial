package main

import (
	"flag"
	"log"
	"server"
)

func main() {
	port := flag.String("p", "8090", "Port to listen on")
	dbPath := flag.String("d", ".", "Path to database directory")
	flag.Parse()

	log.Printf("Starting TorrServer on port %s with DB at %s", *port, *dbPath)
	
	// Start(pathdb, port string, roSets, searchWA bool)
	server.Start(*dbPath, *port, false, false)
	
	// Wait until the server stops or encounters an error
	err := server.WaitServer()
	if err != "" {
		log.Fatalf("Server error: %s", err)
	}
}
