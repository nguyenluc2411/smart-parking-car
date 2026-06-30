package com.smartparking.parking.entity.enums;

/** Physical barrier state (DB column gates.status). BR-006-1: only OPEN/CLOSED, plus ERROR. */
public enum GateStatus {
    OPEN,
    CLOSED,
    ERROR
}
