package com.gordonpn.anime.models;

import com.gordonpn.anime.models.userlist.Status;

import java.util.List;

/** Statuses requested for synchronization whose snapshots were not refreshed. */
public record SyncResult(List<Status> skippedStatuses) {
    public SyncResult {
        skippedStatuses = List.copyOf(skippedStatuses);
    }
}
