package com.gordonpn.anime.commands;

import com.gordonpn.anime.models.AnimeList;
import com.gordonpn.anime.models.SyncResult;
import java.util.List;
import com.gordonpn.anime.models.userlist.Status;
import com.gordonpn.anime.service.BackgroundFetchService;
import com.gordonpn.anime.service.UserListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ListCommandsTests {

    @Mock
    private UserListService userListService;

    @Mock
    private BackgroundFetchService backgroundFetchService;

    private ListCommands listCommands;

    @BeforeEach
    public void setUp() {
        listCommands = new ListCommands(userListService, backgroundFetchService);
    }

    @Test
    public void testListCommand() throws Exception {
        AnimeList mockList = new AnimeList();
        mockList.setData(new ArrayList<>());

        when(userListService.getListByUserName("testUser", Status.COMPLETED)).thenReturn(mockList);

        AnimeList result = listCommands.list("testUser");

        assertNotNull(result);
        verify(userListService, times(1)).getListByUserName("testUser", Status.COMPLETED);
    }

    @Test
    public void testSyncCommand() throws Exception {
        when(backgroundFetchService.forceFetchList()).thenReturn(new SyncResult(List.of()));
        String result = listCommands.sync();

        assertEquals("List sync completed successfully!", result);
        verify(backgroundFetchService, times(1)).forceFetchList();
    }
    @Test
    void incompleteSyncNamesSkippedStatusesWithoutClaimingSuccess() {
        when(backgroundFetchService.forceFetchList()).thenReturn(new SyncResult(List.of(Status.COMPLETED)));
        String result = listCommands.sync();
        org.junit.jupiter.api.Assertions.assertTrue(result.contains("incomplete"));
        org.junit.jupiter.api.Assertions.assertTrue(result.contains("completed"));
        org.junit.jupiter.api.Assertions.assertFalse(result.contains("successfully"));
    }
}
