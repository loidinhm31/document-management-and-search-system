import { createAsyncThunk, createSlice } from "@reduxjs/toolkit";

import { masterDataService, MasterDataType } from "@/services/master-data.service";
import { RootState } from "@/store";
import { MasterData } from "@/types/document";

export interface MasterDataState {
  majors: MasterData[];
  levels: MasterData[];
  categories: MasterData[];
  loading: boolean;
  error: string | null;
}

const initialState: MasterDataState = {
  majors: [],
  levels: [],
  categories: [],
  loading: false,
  error: null
};

export const fetchMasterData = createAsyncThunk(
  "masterData/fetchAll",
  async (_, { rejectWithValue }) => {
    try {
      const [majorsResponse, levelsResponse, categoriesResponse] = await Promise.all([
        masterDataService.getByType(MasterDataType.MAJOR),
        masterDataService.getByType(MasterDataType.COURSE_LEVEL),
        masterDataService.getByType(MasterDataType.DOCUMENT_CATEGORY)
      ]);

      return {
        majors: majorsResponse.data,
        levels: levelsResponse.data,
        categories: categoriesResponse.data
      };
    } catch (error) {
      return rejectWithValue(error.message);
    }
  }
);

const masterDataSlice = createSlice({
  name: "masterData",
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(fetchMasterData.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchMasterData.fulfilled, (state, action) => {
        state.loading = false;
        state.majors = action.payload.majors;
        state.levels = action.payload.levels;
        state.categories = action.payload.categories;
      })
      .addCase(fetchMasterData.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      });
  }
});

export const selectMasterData = (state: RootState) => state.masterData;

export default masterDataSlice.reducer;