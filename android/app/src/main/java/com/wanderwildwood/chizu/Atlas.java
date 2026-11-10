package com.wanderwildwood.chizu;

/** What Atlas leaves out of CoMaps, in one place, so the upstream code stays as it is. */
public final class Atlas
{
  /**
   * Map editing (add or edit a place, notes for mappers) and the OpenStreetMap sign-in it needs.
   * Off: Atlas has no account of any kind. Every way in checks this; the editor's own code is
   * untouched and unreachable.
   */
  public static final boolean EDITING = false;

  private Atlas() {}
}
