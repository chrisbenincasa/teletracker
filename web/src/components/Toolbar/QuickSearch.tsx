import React, { useRef } from 'react';
import {
  CircularProgress,
  Chip,
  ClickAwayListener,
  Collapse,
  Fade,
  Icon,
  makeStyles,
  MenuList,
  MenuItem,
  Paper,
  Popper,
  Theme,
  Typography,
} from '@material-ui/core';
import { Rating } from '@material-ui/lab';
import { Item } from '../../types/v2/Item';
import { truncateText } from '../../utils/textHelper';
import RouterLink from '../RouterLink';
import { getTmdbPosterImage } from '../../utils/image-helper';
import { formatRuntime } from '../../utils/textHelper';
import moment from 'moment';

const useStyles = makeStyles((theme: Theme) => ({
  chip: {
    margin: theme.spacing(0.5, 0.5, 0.5, 0),
  },
  chipWrapper: {
    display: 'flex',
    flexDirection: 'row',
    alignItems: 'center',
  },
  itemDetails: {
    display: 'flex',
    flexDirection: 'column',
  },
  missingPoster: {
    display: 'flex',
    width: 50,
    marginRight: theme.spacing(1),
    height: 75,
    color: theme.palette.grey[500],
    backgroundColor: theme.palette.grey[300],
    fontSize: '3em',
  },
  missingPosterIcon: {
    alignSelf: 'center',
    margin: '0 auto',
    display: 'inline-block',
  },
  noResults: {
    padding: theme.spacing(1),
    alignSelf: 'center',
  },
  poster: {
    width: 50,
    marginRight: theme.spacing(1),
  },
  searchWrapper: props => ({
    height: 'auto',
    overflow: 'scroll',
    width: '100%',
    maxWidth: 720,
    marginTop: 10,
    backgroundColor:
      props.color && props.color === 'secondary'
        ? theme.palette.secondary.main
        : theme.palette.primary.main,
  }),
  viewAllResults: {
    justifyContent: 'center',
  },
  popper: {
    zIndex: theme.zIndex.appBar - 1,
  },
}));

interface Props {
  isSearching: boolean;
  searchAnchor?: HTMLInputElement | null;
  searchResults?: Item[];
  searchText: string;
  handleResetSearchAnchor: (event) => void;
  handleSearchForSubmit: (event) => void;
  color?: string;
}

function QuickSearch(props: Props) {
  const classes = useStyles(props);
  const quickSearchContainer = useRef<HTMLDivElement>(null);
  let containerHeight =
    quickSearchContainer &&
    quickSearchContainer.current &&
    quickSearchContainer.current.clientHeight &&
    quickSearchContainer.current.clientHeight > 72
      ? quickSearchContainer.current.clientHeight - 16
      : 56;
  // Above logic just ensures the container has enough height to be visible
  // 16 = padding on container
  // 56 = current height of No Results messaging
  // 72 = 56+16
  let { searchResults, isSearching, searchText, searchAnchor } = props;

  return searchAnchor && searchText && searchText.length > 0 ? (
    <ClickAwayListener
      onClickAway={event => props.handleResetSearchAnchor(event)}
    >
      <Popper
        open={!!searchAnchor}
        anchorEl={searchAnchor}
        placement="bottom-start"
        // keepMounted
        transition
        disablePortal
        style={{ width: '100%' }}
        className={classes.popper}
      >
        {({ TransitionProps, placement }) => (
          <Fade
            {...TransitionProps}
            style={{
              transformOrigin:
                placement === 'bottom' ? 'center top' : 'center bottom',
            }}
            in={!!searchAnchor}
          >
            <Paper
              id="menu-list-grow"
              className={classes.searchWrapper}
              elevation={5}
              ref={quickSearchContainer}
            >
              <MenuList>
                {isSearching ? (
                  <div
                    style={{
                      height: `${containerHeight}px`,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                  >
                    <CircularProgress color="secondary" />
                  </div>
                ) : (
                  <div>
                    <Collapse
                      in={!isSearching && !!searchResults}
                      timeout={500}
                    >
                      {searchResults && searchResults.length > 0 ? (
                        searchResults.slice(0, 4).map(result => {
                          const voteAverage =
                            result.ratings && result.ratings.length
                              ? result.ratings[0].vote_average
                              : 0;
                          const runtime =
                            (result.runtime &&
                              formatRuntime(result.runtime, result.type)) ||
                            null;
                          const rating =
                            result.release_dates &&
                            result.release_dates.find(item => {
                              if (
                                item.country_code === 'US' &&
                                item.certification !== 'NR'
                              ) {
                                return item.certification;
                              } else {
                                return null;
                              }
                            });

                          return (
                            <MenuItem
                              dense
                              component={RouterLink}
                              to={result.relativeUrl}
                              key={result.id}
                              onClick={event =>
                                props.handleResetSearchAnchor(event)
                              }
                            >
                              {getTmdbPosterImage(result) ? (
                                <img
                                  alt={`Movie poster for ${result.canonicalTitle}`}
                                  src={`https://image.tmdb.org/t/p/w92${
                                    getTmdbPosterImage(result)!.id
                                  }`}
                                  className={classes.poster}
                                />
                              ) : (
                                <div className={classes.missingPoster}>
                                  <Icon
                                    className={classes.missingPosterIcon}
                                    fontSize="inherit"
                                  >
                                    broken_image
                                  </Icon>
                                </div>
                              )}
                              <div className={classes.itemDetails}>
                                <Typography variant="subtitle1">
                                  {truncateText(result.canonicalTitle, 100)}
                                </Typography>
                                <Rating
                                  value={voteAverage / 2}
                                  precision={0.1}
                                  size="small"
                                  readOnly
                                />
                                <div className={classes.chipWrapper}>
                                  <Chip
                                    label={result.type}
                                    clickable
                                    size="small"
                                    className={classes.chip}
                                  />
                                  {rating && rating.certification && (
                                    <Chip
                                      label={rating.certification}
                                      clickable
                                      size="small"
                                      className={classes.chip}
                                    />
                                  )}
                                  {result.release_date && (
                                    <Chip
                                      label={moment(result.release_date).format(
                                        'YYYY',
                                      )}
                                      clickable
                                      size="small"
                                      className={classes.chip}
                                    />
                                  )}
                                  {runtime && (
                                    <Chip
                                      label={runtime}
                                      clickable
                                      size="small"
                                      className={classes.chip}
                                    />
                                  )}
                                </div>
                              </div>
                            </MenuItem>
                          );
                        })
                      ) : (
                        <Typography
                          variant="body1"
                          align="center"
                          className={classes.noResults}
                        >
                          No results matching that search
                        </Typography>
                      )}
                      {searchResults && searchResults.length > 4 && (
                        <MenuItem
                          dense
                          className={classes.viewAllResults}
                          onClick={props.handleSearchForSubmit}
                          key="view-all"
                        >
                          View All Results
                        </MenuItem>
                      )}
                    </Collapse>
                  </div>
                )}
              </MenuList>
            </Paper>
          </Fade>
        )}
      </Popper>
    </ClickAwayListener>
  ) : null;
}

export default QuickSearch;
