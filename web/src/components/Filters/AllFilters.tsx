import React, { useState } from 'react';
import {
  Collapse,
  ExpansionPanel,
  ExpansionPanelSummary,
  ExpansionPanelDetails,
  makeStyles,
  Paper,
  Theme,
  Typography,
} from '@material-ui/core';
import { ExpandMore } from '@material-ui/icons';
import { useWidth } from '../../hooks/useWidth';
import {
  Genre,
  Network,
  ItemType,
  SortOptions,
  NetworkType,
} from '../../types';
import TypeToggle from './TypeToggle';
import NetworkSelect from './NetworkSelect';
import GenreSelect from './GenreSelect';
import SortDropdown from './SortDropdown';
import Sliders, { SliderChange } from './Sliders';
import CreateSmartListButton from '../Buttons/CreateSmartListButton';
import CreateDynamicListDialog from '../Dialogs/CreateDynamicListDialog';
import { FilterParams } from '../../utils/searchFilters';
import { filterParamsEqual } from '../../utils/changeDetection';
import PersonFilter from './PersonFilter';

const useStyles = makeStyles((theme: Theme) => ({
  actionButtons: {
    display: 'flex',
    flexGrow: 1,
    justifyContent: 'flex-end',
    marginTop: theme.spacing(1),
  },
  allFiltersContainer: {
    marginTop: theme.spacing(1),
    padding: theme.spacing(2),
    [theme.breakpoints.down('sm')]: {
      padding: 0,
    },
  },
  filterSortContainer: {
    [theme.breakpoints.up('sm')]: {
      display: 'flex',
    },
    [theme.breakpoints.down('sm')]: {
      display: 'flex',
      flexDirection: 'column',
    },
    zIndex: theme.zIndex.mobileStepper,
    marginBottom: theme.spacing(1),
    flexGrow: 1,
  },
  filterTitle: {
    display: 'block',
    width: '100%',
  },
  genreContainer: {
    display: 'flex',
    flexDirection: 'row',
    width: '40%',
    flexWrap: 'wrap',
    margin: theme.spacing(1, 0),
    [theme.breakpoints.down('sm')]: {
      width: '100%',
    },
  },
  peopleContainer: {
    // TODO: Figure out why flexbox doesn't play nicely with the absolute
    // positioning of the Autocomplete box
    // display: 'flex',
    flexGrow: 1,
    margin: theme.spacing(1, 0),
    alignItems: 'flex-start',
    width: '100%',
  },
  slidersContainer: {
    display: 'flex',
    flexGrow: 1,
    margin: theme.spacing(0, 3),
    [theme.breakpoints.down('md')]: {
      margin: 0,
    },
    alignItems: 'flex-start',
    flexDirection: 'column',
    width: '20%',
    [theme.breakpoints.down('sm')]: {
      width: '100%',
    },
  },
  sliderContainer: {
    display: 'flex',
    flexGrow: 1,
    margin: theme.spacing(1, 0),
    alignItems: 'flex-start',
    width: '100%',
  },
  networkContainer: {
    display: 'flex',
    margin: theme.spacing(1, 0),
    alignItems: 'flex-start',
    flexGrow: 1,
    flexDirection: 'column',
    width: '40%',
    [theme.breakpoints.down('sm')]: {
      width: '100%',
    },
    '& > div': {
      marginBottom: theme.spacing(1),
    },
  },
  toEdgeWrapper: {
    margin: theme.spacing(0, 0, 2),
    [theme.breakpoints.down('sm')]: {
      margin: theme.spacing(0.5, 0, 1),
    },
  },
  typeContainer: {
    display: 'flex',
    margin: theme.spacing(1, 0),
    alignItems: 'flex-start',
    flexGrow: 1,
  },
  sortContainer: {
    display: 'flex',
    flexDirection: 'column',
    margin: theme.spacing(1),
    alignItems: 'flex-start',
  },
}));

interface Props {
  disableSliders?: boolean;
  disableSortOptions?: boolean;
  disableNetworks?: boolean;
  disableTypeChange?: boolean;
  disableGenres?: boolean;
  updateFilters: (filterParams: FilterParams) => void;
  filters: FilterParams;
  isListDynamic?: boolean;
  genres?: Genre[];
  open: boolean;
  disabledGenres?: number[];
  sortOptions?: SortOptions[];
  networks?: Network[];
  listFilters?: FilterParams;
}

const AllFilters = (props: Props) => {
  const classes = useStyles();
  const TIMEOUT_ENTER = 300;
  const TIMEOUT_EXIT = 300;
  const {
    disabledGenres,
    genres,
    isListDynamic,
    open,
    disableSliders,
    disableSortOptions,
    disableNetworks,
    disableTypeChange,
    disableGenres,
    updateFilters,
    filters,
    sortOptions,
  } = props;

  const width = useWidth();
  const isMobile = ['xs', 'sm'].includes(width);

  const [smartListOpen, setSmartListOpen] = useState<boolean>(false);

  const handleFilterUpdate = (newFilter: FilterParams) => {
    if (!filterParamsEqual(filters, newFilter)) {
      updateFilters(newFilter);
    }
  };

  const setGenre = (genres?: number[]) => {
    handleFilterUpdate({ ...filters, genresFilter: genres });
  };

  const setType = (type?: ItemType[]) => {
    handleFilterUpdate({ ...filters, itemTypes: type });
  };

  const setNetworks = (networks?: NetworkType[]) => {
    handleFilterUpdate({ ...filters, networks });
  };

  const setSort = (sortOrder: SortOptions) => {
    handleFilterUpdate({ ...filters, sortOrder });
  };

  const setSliders = (sliderChange: SliderChange) => {
    let newFilters = {
      ...filters,
      sliders: {
        ...(filters.sliders || {}),
        releaseYear: sliderChange.releaseYear,
      },
    };

    handleFilterUpdate(newFilters);
  };

  const setPeople = (people: string[]) => {
    handleFilterUpdate({
      ...filters,
      people,
    });
  };

  const renderCreateSmartListDialog = () => {
    return (
      <React.Fragment>
        <CreateDynamicListDialog
          filters={props.filters}
          open={smartListOpen}
          onClose={() => setSmartListOpen(false)}
          networks={props.networks || []}
          genres={props.genres || []}
        />
      </React.Fragment>
    );
  };

  const actionButtons = () => {
    return (
      <div className={classes.actionButtons}>
        <CreateSmartListButton
          filters={props.filters}
          listFilters={props.listFilters}
          onClick={() => setSmartListOpen(true)}
          isListDynamic={props.isListDynamic}
        />
        {renderCreateSmartListDialog()}
      </div>
    );
  };

  const mobileFilters = () => {
    return (
      <Collapse
        in={open}
        timeout={{
          enter: TIMEOUT_ENTER,
          exit: TIMEOUT_EXIT,
        }}
        className={classes.toEdgeWrapper}
        appear
      >
        {!disableGenres && (
          <ExpansionPanel square>
            <ExpansionPanelSummary
              expandIcon={<ExpandMore />}
              aria-controls="genre-content"
              id="genre-header"
            >
              <Typography>Genres</Typography>
            </ExpansionPanelSummary>
            <ExpansionPanelDetails>
              <div className={classes.genreContainer}>
                <GenreSelect
                  genres={genres}
                  disabledGenres={disabledGenres}
                  handleChange={setGenre}
                  selectedGenres={filters.genresFilter || []}
                  showTitle={false}
                />
              </div>
            </ExpansionPanelDetails>
          </ExpansionPanel>
        )}

        {!disableSliders ? (
          <ExpansionPanel square>
            <ExpansionPanelSummary
              expandIcon={<ExpandMore />}
              aria-controls="slider-content"
              id="slider-header"
            >
              <Typography>Release Year</Typography>
            </ExpansionPanelSummary>
            <ExpansionPanelDetails>
              <div className={classes.slidersContainer}>
                <div className={classes.sliderContainer}>
                  <Sliders
                    handleChange={setSliders}
                    sliders={filters.sliders}
                    showTitle={false}
                  />
                </div>
              </div>
            </ExpansionPanelDetails>
          </ExpansionPanel>
        ) : null}

        <ExpansionPanel square>
          <ExpansionPanelSummary
            expandIcon={<ExpandMore />}
            aria-controls="people-content"
            id="people-header"
          >
            <Typography>People</Typography>
          </ExpansionPanelSummary>
          <ExpansionPanelDetails>
            <div className={classes.peopleContainer}>
              <PersonFilter
                handleChange={setPeople}
                selectedCast={filters.people}
                showTitle={false}
              />
            </div>
          </ExpansionPanelDetails>
        </ExpansionPanel>

        {!disableNetworks && (
          <ExpansionPanel square>
            <ExpansionPanelSummary
              expandIcon={<ExpandMore />}
              aria-controls="networks-content"
              id="networks-header"
            >
              <Typography>Networks</Typography>
            </ExpansionPanelSummary>
            <ExpansionPanelDetails>
              <NetworkSelect
                selectedNetworks={filters.networks}
                handleChange={setNetworks}
                showTitle={false}
              />
            </ExpansionPanelDetails>
          </ExpansionPanel>
        )}

        {!disableTypeChange && (
          <ExpansionPanel square>
            <ExpansionPanelSummary
              expandIcon={<ExpandMore />}
              aria-controls="type-content"
              id="type-header"
            >
              <Typography>Content Type</Typography>
            </ExpansionPanelSummary>
            <ExpansionPanelDetails>
              <TypeToggle
                selectedTypes={filters.itemTypes}
                handleChange={setType}
                showTitle={false}
              />
            </ExpansionPanelDetails>
          </ExpansionPanel>
        )}

        {!disableSortOptions && (
          <ExpansionPanel square>
            <ExpansionPanelSummary
              expandIcon={<ExpandMore />}
              aria-controls="sort-content"
              id="sort-header"
            >
              <Typography>Sort</Typography>
            </ExpansionPanelSummary>
            <ExpansionPanelDetails>
              <SortDropdown
                isListDynamic={!!isListDynamic}
                handleChange={setSort}
                selectedSort={filters.sortOrder}
                validSortOptions={sortOptions}
                showTitle={false}
              />
            </ExpansionPanelDetails>
          </ExpansionPanel>
        )}

        {actionButtons()}
      </Collapse>
    );
  };

  const desktopFilters = () => {
    return (
      <Collapse
        in={open}
        timeout={{
          enter: TIMEOUT_ENTER,
          exit: TIMEOUT_EXIT,
        }}
        className={classes.toEdgeWrapper}
        appear
      >
        <Paper
          id="all-filters"
          elevation={5}
          className={classes.allFiltersContainer}
        >
          <div className={classes.filterSortContainer}>
            <div className={classes.genreContainer}>
              {!disableGenres && (
                <GenreSelect
                  genres={genres}
                  disabledGenres={disabledGenres}
                  handleChange={setGenre}
                  selectedGenres={filters.genresFilter || []}
                />
              )}
            </div>

            <div className={classes.slidersContainer}>
              <div className={classes.sliderContainer}>
                {!disableSliders ? (
                  <Sliders
                    handleChange={setSliders}
                    sliders={filters.sliders}
                  />
                ) : null}
              </div>
              <div className={classes.peopleContainer}>
                <PersonFilter
                  handleChange={setPeople}
                  selectedCast={filters.people}
                />
              </div>
            </div>

            <div className={classes.networkContainer}>
              {!disableNetworks && (
                <NetworkSelect
                  selectedNetworks={filters.networks}
                  handleChange={setNetworks}
                />
              )}
              {!disableTypeChange && (
                <TypeToggle
                  selectedTypes={filters.itemTypes}
                  handleChange={setType}
                />
              )}
              {!disableSortOptions && (
                <SortDropdown
                  isListDynamic={!!isListDynamic}
                  handleChange={setSort}
                  selectedSort={filters.sortOrder}
                  validSortOptions={sortOptions}
                />
              )}
            </div>
          </div>
          {actionButtons()}
        </Paper>
      </Collapse>
    );
  };

  return isMobile ? mobileFilters() : desktopFilters();
};

export default AllFilters;
