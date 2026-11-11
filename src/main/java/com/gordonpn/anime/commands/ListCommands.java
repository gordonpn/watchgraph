package com.gordonpn.anime.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.gordonpn.anime.models.AnimeList;
import com.gordonpn.anime.models.userlist.Status;
import com.gordonpn.anime.service.BackgroundFetchService;
import com.gordonpn.anime.service.UserListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
@RequiredArgsConstructor
@Slf4j
public class ListCommands {
    private final UserListService userListService;
    private final BackgroundFetchService backgroundFetchService;

    @ShellMethod(key = "list", value = "List all completed anime for a user.")
    public AnimeList list(@ShellOption(defaultValue = "@me", help = "The MyAnimeList username, or '@me' to use the local SQLite database") String userName) {
        AnimeList listByUserName = userListService.getListByUserName(userName, Status.COMPLETED);
        log.info("List length fetched: {}", listByUserName.getData().size());
        return listByUserName;
    }

    @ShellMethod(key = "sync", value = "Force synchronization of all anime lists from MyAnimeList to the local SQLite database.")
    public String sync() {
        log.info("Triggering list synchronization");
        backgroundFetchService.forceFetchList();
        return "List sync completed successfully!";
    }
}