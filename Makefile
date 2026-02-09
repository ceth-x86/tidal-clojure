CATALOG_DIR = ~/Documents/tidal-music-catalog

albums:
	lein run albums --output $(CATALOG_DIR)/albums.csv

artists:
	lein run artists --output $(CATALOG_DIR)/artists.csv

playlists_mine:
	lein run playlists mine --output $(CATALOG_DIR)/playlists_mine.csv

playlists_mine_detail:
	lein run playlists mine --detail --output $(CATALOG_DIR)/playlists

playlists_mine_one:
	lein run playlists mine --detail --id $(ID) --output $(CATALOG_DIR)/playlists

playlists_saved:
	lein run playlists saved --output $(CATALOG_DIR)/saved_playlists.csv
