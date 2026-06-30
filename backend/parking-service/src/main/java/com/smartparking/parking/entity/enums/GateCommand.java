package com.smartparking.parking.entity.enums;

/** Command issued to a gate (DB column gate_logs.command, gate.command event). */
public enum GateCommand {
    OPEN,
    CLOSE
}
