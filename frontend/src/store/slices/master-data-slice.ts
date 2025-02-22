import { createAsyncThunk, createSlice } from "@reduxjs/toolkit";

import { masterDataService } from "@/services/master-data.service";
import { RootState } from "@/store";
import { MasterData, MasterDataType } from "@/types/master-data";

export interface MasterDataState {
  majors: MasterData[];
  courseCodes: MasterData[];
  levels: MasterData[];
  categories: MasterData[];
  loading: boolean;
  error: string | null;
}

const initialState: MasterDataState = {
  majors: [],
  courseCodes: [],
  levels: [],
  categories: [],
  loading: false,
  error: null
};

export const fetchMasterData = createAsyncThunk(
  "masterData/fetchAll",
  async (_, { rejectWithValue }) => {
    try {
      const [majorsResponse, courseCodesReponse, levelsResponse, categoriesResponse] = await Promise.all([
        masterDataService.getAllByType(MasterDataType.MAJOR, true),
        masterDataService.getAllByType(MasterDataType.COURSE_CODE, true),
        masterDataService.getAllByType(MasterDataType.COURSE_LEVEL, true),
        masterDataService.getAllByType(MasterDataType.DOCUMENT_CATEGORY, true)
      ]);

      return {
        majors: majorsResponse.data,
        courseCodes: courseCodesReponse.data,
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
        state.courseCodes = action.payload.courseCodes;
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